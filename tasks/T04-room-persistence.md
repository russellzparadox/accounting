# T04 — room-persistence
Status: in_progress
Depends: T03
Read first: CLAUDE.md, handouts/T03-domain-core.md
Skills to invoke: none

## Goal
- Room entities, DAOs, repositories. Seed chart of accounts. Fiscal year and sequential doc numbers. Enforce immutability of committed entries.

## Deliverables (files)
- `core/data/src/main/kotlin/.../db/` entities, DAOs, `AppDatabase.kt`, migrations
- `core/data/src/main/kotlin/.../repo/` repositories
- `core/data/src/main/kotlin/.../seed/ChartSeeder.kt` (reads `chart_of_accounts.json`)
- `core/data/src/test/` or `androidTest/` DAO tests

## Acceptance criteria
- [ ] Entities: Account, JournalEntry, JournalLine, FiscalYear, DocSequence.
- [ ] Money columns are `INTEGER` (rials). No `REAL` columns (grep).
- [ ] Dates stored as ISO Gregorian `TEXT`.
- [ ] Seeder is idempotent. Second run adds no rows (test).
- [ ] Doc numbers are sequential per fiscal year, allocated in a transaction, no gaps (test).
- [ ] Committed journal rows cannot be UPDATE or DELETE. Enforced by DAO API and by DB triggers (tests).
- [ ] Test runner (Robolectric or instrumented) and reason recorded in handout.

## Out of scope
- UI, LLM, voice, reports.

## Verify (commands)
- `./gradlew :core:data:test`
- `grep -rE "REAL|Float|Double" core/data/src/main` (expect no money matches)

## Added by main agent
- Also read `handouts/T05-persian-utils.md` (use `JalaliDate.fiscalYear(year)` for default fiscal year creation; `core:data` depends on `:core:domain`, may depend on `:core:persian`) and `handouts/T01d-ci-green.md` (how to read CI).
- Google Maven is blocked locally; Room/KSP/Robolectric cannot be built here. CI is the ONLY verification. You may `git add/commit/push` to `claude/dreamy-mccarthy-mczm8c` (commit prefix `T04:`, no force-push, no other branches) and iterate with the `android` workflow exactly as T01d did (use mcp__github__ tools, small `tail_lines` <= 80, the workflow already prints a failure summary). Max 8 iterations. Never skip/disable tests.
- Use Room + KSP (`ksp` plugin already in catalog; add Room versions to `gradle/libs.versions.toml`; verify versions exist on Maven Central/Google Maven via CI). Tests: Robolectric + in-memory Room under `core/data/src/test` (so `./gradlew test` in CI runs them). Record the reason in handout.
- Implement domain ports: `PartyAccountResolver` (T03) backed by DB: find-or-create معین under 1300 (codes 1301..1349) / 2100 (2101..2199) by `partyKey(name)`; persist `party` table (kind, key, displayName, accountCode) and the created Account row; deterministic next-code allocation; error when range exhausted.
- Repository API for upper layers (document signatures in handout): `LedgerRepository.commit(entry: JournalEntry): CommittedDoc` (allocates doc number per fiscal year in the same transaction, persists lines, rejects unbalanced/out-of-fiscal-year), `reverse(docId, date, memo)` (uses domain `PostingEngine.reverse`, new doc number), `journal(fiscalYear, from?, to?)` Flow, `ledger(accountCode, range)` with running balance Flow, `trialBalance(fiscalYear, asOf?)` -> rows (code, name, debit, credit) with totals equal, `accounts()` Flow, `ensureFiscalYear(jalaliYear)`.
- Triggers: BEFORE UPDATE/DELETE on journal_entry and journal_line raise ABORT (reversal only creates new docs). Test with raw SQL.
