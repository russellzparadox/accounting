# Handout T04 — room-persistence
Status: partial (code pushed; CI conclusion NOT obtained)

## Summary
- Room 2.7.2 + KSP in `core:data`. In-memory Room + Robolectric tests under `core/data/src/test`.
- DB v1, `exportSchema=true`, schema dir `core/data/schemas` (KSP arg `room.schemaLocation`).
- Immutability: no @Update/@Delete on journal DAOs; BEFORE UPDATE/DELETE triggers RAISE(ABORT) on `journal_entry` and `journal_line`.
- Doc numbers per fiscal year allocated inside the commit transaction (no gaps on failure).
- Seeder idempotent. Party resolver find-or-create in 1301..1349 / 2101..2199.
- Nothing compiled or run locally (Google Maven blocked). CI is the only verifier.

## Files
- `core/data/build.gradle.kts`: + ksp, room runtime/ktx, ksp room compiler, core:persian, coroutines, junit/robolectric/androidx.test.core (test), `unitTests.isIncludeAndroidResources`.
- `gradle/libs.versions.toml`: + room 2.7.2, kotlinxCoroutines 1.10.2, junit 4.13.2, robolectric 4.14.1, androidxTest 1.6.1 and matching libs.
- `core/data/src/main/kotlin/com/example/accounting/core/data/`
  - `db/Entities.kt`: AccountEntity, PartyEntity, FiscalYearEntity, DocSequenceEntity, JournalEntryEntity, JournalLineEntity. (Suffix `Entity` avoids clash with domain `Account`/`JournalEntry`.)
  - `db/Daos.kt`: AccountDao, PartyDao, FiscalYearDao, DocSequenceDao, JournalDao; tuples EntryWithLines, LedgerRowTuple, TrialTuple.
  - `db/AppDatabase.kt`: `AppDatabase.file(context, name)`, `AppDatabase.inMemory(context)`; `IMMUTABILITY_TRIGGERS`; `IntegrityCallback` (onOpen: `PRAGMA foreign_keys=ON`, triggers `IF NOT EXISTS`).
  - `repo/LedgerRepository.kt`, `repo/PartyRepository.kt`, `seed/ChartSeeder.kt`.
- `core/data/src/test/kotlin/.../core/data/`: ChartSeederTest, PartyRepositoryTest, LedgerRepositoryTest.
- No change to core:domain or core:persian.

## Repository signatures (package `com.example.accounting.core.data.repo`)
```kotlin
class LedgerRepository(db: AppDatabase, chart: ChartOfAccounts = ChartOfAccounts.standard(), idGenerator: () -> String = UUID)
suspend fun ensureFiscalYear(jalaliYear: Int): FiscalYear                       // idempotent
suspend fun commit(entry: JournalEntry): CommittedDoc                           // one txn; doc no. per Jalali FY of entry.date
suspend fun reverse(docId: String, date: LocalDate, memo: String? = null): CommittedDoc  // once per doc
fun journal(fiscalYear: Int, from: LocalDate? = null, to: LocalDate? = null): Flow<List<CommittedDoc>>
fun ledger(accountCode: String, from: LocalDate? = null, to: LocalDate? = null): Flow<List<LedgerLine>>
suspend fun trialBalance(fiscalYear: Int, asOf: LocalDate? = null): TrialBalance
fun accounts(): Flow<List<Account>>

data class CommittedDoc(id: String, fiscalYear: Int, docNumber: Int, entry: JournalEntry)
data class LedgerLine(date, fiscalYear, docNumber, entryId, memo, debit: Long, credit: Long, balance: Long)
data class TrialRow(code, name, debit: Long, credit: Long)
data class TrialBalance(rows: List<TrialRow>, debitTotal: Long, creditTotal: Long)

class PartyRepository(db: AppDatabase) : PartyAccountResolver   // resolve() is BLOCKING (runBlocking); never call on main thread
class ChartSeeder(db: AppDatabase, chart: ChartOfAccounts = ChartOfAccounts.standard()) { suspend fun seed(): Int }  // rows inserted
```
Errors: `DomainException` for unopened FY, date outside FY, non-postable account, missing or already-reversed target, party range exhausted. Duplicate id → `SQLiteConstraintException`.

## DB schema (v1)
- `account(code PK, name_fa, level, parent, type, normal_balance)`. Enum columns hold Kotlin enum names (GROUP/GENERAL/DETAIL, DEBIT/CREDIT). Index on parent.
- `party(kind, party_key, display_name, account_code UNIQUE FK→account)`, PK (kind, party_key).
- `fiscal_year(jalali_year PK, start_date TEXT, end_date TEXT)`.
- `doc_sequence(fiscal_year PK FK, last_number)`.
- `journal_entry(id PK, entry_date TEXT ISO, fiscal_year FK, doc_number, template_id, memo, reversal_of_id)`. UNIQUE(fiscal_year, doc_number). UNIQUE(reversal_of_id) (NULLs allowed).
- `journal_line(id PK autoinc, entry_id FK, account_code FK, side, amount INTEGER rial)`.
- Triggers: `journal_entry_no_update`, `journal_entry_no_delete`, `journal_line_no_update`, `journal_line_no_delete` (BEFORE, RAISE ABORT). Created in onOpen, not in Room schema.
- Not in schema: CHECK(amount > 0) (Room cannot express it; domain JournalLine enforces it).

## Decisions (+why)
- Entity class suffix `Entity` (name clash with domain types).
- Money INTEGER rials, dates ISO TEXT. No REAL/Float/Double in core/data/src/main (grep clean).
- Triggers via RoomDatabase.Callback.onOpen, IF NOT EXISTS: Room annotations cannot express triggers. Runs on create and every open.
- Party codes: max(existing code in range) + 1, starting at range start. Sample معین 1201/1301/2101 exist in chart, so first party = 1302 / 2102.
- PartyAccountResolver is synchronous per domain port, so PartyRepository uses runBlocking + synchronized lock.
- `ledger` balance = running (debit minus credit), opening = all lines dated before `from` across FYs; 0 if from is null.
- `trialBalance` = gross debit/credit per account for the FY; totals equal for balanced data.
- reverse() uses domain `PostingEngine.reverse`; reversal is a new doc with new number; reversing twice blocked (DB unique + explicit check).
- Test runner: Robolectric 4.14.1, JUnit4, `@Config(sdk=[34])`, in-memory Room (real SQLite). Chosen over instrumented tests so `./gradlew test` runs them in CI.
- Schema export on; `core/data/schemas/*.json` is generated by the build, not committed (not produced locally).

## How verified
- Local: nothing (Google Maven blocked; Room/KSP/Robolectric need it).
- `grep -rnE "REAL|Float|Double" core/data/src/main` → no matches (exit 1).
- CI run (commit 1096a23): https://github.com/russellzparadox/accounting/actions/runs/41183817673 — status `pending`, no conclusion at handout time; no job started.
- Same branch also has run 45 (WIP commit 4b6f24c, https://github.com/russellzparadox/accounting/actions/runs/41183666065) `in_progress` for a long time. Likely runner queue, not a code failure. Not investigated.

## Open issues / risks
1. CI conclusion unknown. Dependency coordinates (room 2.7.2, robolectric 4.14.1, androidx.test:core 1.6.1) and KSP 2.2.20-2.0.4 compatibility unverified; first CI failure is the first real check.
2. Robolectric SDK 34 pinned; if android-all download or SDK support fails, adjust `@Config`.
3. `IntegrityCallback` runs `PRAGMA foreign_keys=ON` in onOpen; if Room opens inside a transaction the PRAGMA is a no-op (FKs then unenforced; code checks still apply).
4. Migrations: none (v1). Future schema changes must include trigger re-creation (triggers are IF NOT EXISTS in onOpen, so should survive).
5. Chart seed names are sample text; accountant review pending (T02 open issue).
6. Reverse uses a PartyAccountResolver that throws; correct only because reverse never creates parties.
7. Blocking PartyRepository.resolve: callers must be off main thread.
8. Robolectric + Room not yet proven to run on this stack.

## Next task must know
- T07: `PostingEngine(ChartOfAccounts.standard(), PartyRepository(db), fiscalYear)` where `fiscalYear = ledgerRepo.ensureFiscalYear(jalaliYear)`. Call `post` off main thread (party resolution blocks). Then `ledgerRepo.commit(entry)`.
- T10: `journal(...)` and `ledger(...)` are Flows; ledger balance sign = debit minus credit.
- T11: `trialBalance(fy, asOf)`.
- Do not add Float/Double to core/data.
