# Handout T03 — domain-core
Status: done

## Summary
- `core:domain` is pure Kotlin/JVM (`kotlin("jvm")` + serialization). No `android.*`, no Float/Double in `src/main`.
- `PostingEngine` implements all 14 template ids from ACCOUNTING_RULES §8. Reversal = new entry. `JournalEntry` rejects unbalanced input.
- 26 JVM tests pass incl. fuzz (5000 random valid intents, Dr == Cr, reversal balanced). Run in scratch project (see How verified).

## Files created/changed
- `core/domain/build.gradle.kts` (kotlin jvm, jvmToolchain 21, JUnit5 via kotlin-test-junit5)
- `core/domain/src/main/AndroidManifest.xml` deleted (no longer an Android module)
- `core/domain/src/main/kotlin/com/example/accounting/core/domain/`: `Money.kt`, `Account.kt` (Account, ChartOfAccounts), `JournalLine.kt` (Side, JournalLine), `JournalEntry.kt`, `FiscalYear.kt`, `TransactionIntent.kt`, `PostingEngine.kt`, `DomainException.kt`, `Serialization.kt`
- `core/domain/src/test/kotlin/com/example/accounting/core/domain/`: `PostingEngineTest`, `JournalEntryTest`, `ChartOfAccountsTest`, `TransactionIntentJsonTest`, `MoneyTest`, `PostingFuzzTest`, `TestFixtures`
- `gradle/libs.versions.toml`: + `kotlinxSerialization=1.9.0`, libs `kotlinx-serialization-json`, `kotlin-test-junit5`; plugins `kotlin-jvm`, `kotlin-serialization`
- `build.gradle.kts` (root): + `kotlin-jvm` and `kotlin-serialization` with `apply false` (same pattern as other plugins)
- `tasks/T03-domain-core.md` Status line only

## Public interfaces / contracts
### TransactionIntent JSON (LLM output; T06 produces, T07 consumes)
```json
{"type":"PURCHASE_CASH","amount_rial":5000000,"date":"2026-10-31","paid_with":"CASH",
 "bank_account":null,"party_name":null,"category":null,"memo":"...","confidence":92,
 "needs_clarification":null}
```
- `type`: one of 14 ids: PURCHASE_CASH, PURCHASE_CREDIT, SALE_CASH, SALE_CREDIT, EXPENSE_PAID, EXPENSE_PAYABLE, RECEIPT_FROM_PERSON, PAYMENT_TO_PERSON, BANK_TRANSFER, OWNER_CAPITAL, OWNER_WITHDRAWAL, PURCHASE_RETURN, SALE_RETURN, REVERSAL (REVERSAL is rejected in intents; use `reverse()`).
- `amount_rial`: integer > 0 (rials). Toman input = ×10 done by caller.
- `date`: Gregorian `yyyy-MM-dd`. Must lie in the fiscal year passed to the engine.
- `paid_with`: `"CASH"` (صندوق 1100) | `"BANK"` (بانک 1200 → معین) | `null` = credit mode.
- `bank_account`: optional 4-digit معین under 1200. If omitted and 1200 has exactly one معین (1201), that one is used. Only valid when paid_with=BANK, or BANK_TRANSFER.
- `party_name`: string | null. Required for PURCHASE_CREDIT, SALE_CREDIT, EXPENSE_PAYABLE, RECEIPT_FROM_PERSON, PAYMENT_TO_PERSON. For PURCHASE_RETURN/SALE_RETURN required only when paid_with=null.
- `category`: `RENT|FREIGHT|PHONE|SALARY|UTILITIES|REPAIR|MISC|BANK_FEE` | null. Required for EXPENSE_PAID and EXPENSE_PAYABLE (→ 6100..6800).
- `memo`: string | null.
- `confidence`: Int 0..100 (percent). Not a float.
- `needs_clarification`: `{"question":"..."}` | null. Non-null → engine refuses to post.
- Unknown keys ignored. Bad values → exception on decode.

Field rules per type (what must be set):
| type | paid_with | party_name | category |
|---|---|---|---|
| PURCHASE_CASH, SALE_CASH, OWNER_CAPITAL, OWNER_WITHDRAWAL, BANK_TRANSFER | req | - | - |
| PURCHASE_CREDIT, SALE_CREDIT | null | req | - |
| EXPENSE_PAID | req | - | req |
| EXPENSE_PAYABLE | null | req | req |
| RECEIPT_FROM_PERSON, PAYMENT_TO_PERSON | req | req | - |
| PURCHASE_RETURN, SALE_RETURN | null → credit (party req) / CASH or BANK → cash refund | req only if null | - |

### Kotlin API
- `class PostingEngine(chart: ChartOfAccounts, parties: PartyAccountResolver, fiscalYear: FiscalYear, idGenerator: () -> String = UUID)`
  - `fun post(intent: TransactionIntent): JournalEntry` — pure; does not persist.
  - `fun reverse(original: JournalEntry, date: LocalDate, memo: String? = null): JournalEntry` — new entry, sides swapped, `reversalOfId = original.id`.
- `interface PartyAccountResolver { fun resolve(kind: PartyKind, key: String, displayName: String): String }` — find-or-create معین (T04). Returns 4-digit code inside `kind.codes`.
- `enum PartyKind(controlCode, codes)`: `CUSTOMER("1300", 1301..1349)`, `SUPPLIER("2100", 2101..2199)`.
- `fun partyKey(name: String): String` — match key: ي→ی, ك→ک, ZWNJ and whitespace removed. `displayName` = trimmed original.
- `class ChartOfAccounts`: `ChartOfAccounts.standard()`, `fromJson(text)`, `codes`, `account(code)`, `childrenOf(code)`, `requirePostable(code)`.
- `class JournalEntry(id, date: LocalDate, templateId: TemplateId, memo: String?, lines: List<JournalLine>, reversalOfId: String? = null)`: `lines` immutable copy, `debitTotal`, `creditTotal`. Init throws `IllegalArgumentException` if < 2 lines or Dr != Cr.
- `data class JournalLine(accountCode: String, side: Side, amount: Money)`; `enum Side { DEBIT, CREDIT }`; amount > 0.
- `@JvmInline value class Money(val rial: Long)`; `Money.ZERO`; plus/minus/compareTo.
- `data class FiscalYear(start: LocalDate, end: LocalDate)`; `date in fy` (inclusive). Gregorian only; Jalali → T05.
- `TransactionIntent.fromJson(text)`; `TransactionIntent.serializer()`.
- Errors: `DomainException : IllegalArgumentException` for intent/chart/posting rule violations.

## Decisions made (+why)
- Bank leg posts to معین 1201, not control 1200. Why: ACCOUNTING_RULES §4 says a کل with معین children takes the معین (open issue 8). §8 literal Cr 1200 is therefore not used.
- Added optional `bank_account` to intent (not in original shape). Why: needed to pick a bank معین; defaults to the single one.
- `paid_with` null = credit. PURCHASE_RETURN / SALE_RETURN pick mode from it. Why: keeps the 14 ids and §8 modes without extra fields.
- BANK_TRANSFER: `paid_with` = source account; destination is the other side (CASH→BANK or BANK→CASH). Why: maps to §8 wording "صندوق به بانک" / "بانک به صندوق" with one field.
- Party معین find-or-create is delegated to `PartyAccountResolver` (persistence T04). Engine validates the returned code range. Why: D8.
- Fiscal year: passed in as Gregorian bounds, `post`/`reverse` reject dates outside. Why: D9, no Jalali in this module.
- Entry id from `idGenerator` (UUID default). Why: `reversalOfId` needs a reference; doc numbers are persistence's job.
- kotlinx-serialization-json for chart and intent. Why: single JSON path, snake_case via @SerialName; no hand parser.
- Confidence is Int percent (domain must not use floats).
- Expense categories: enum names as JSON values; mapping in `ExpenseCategory.accountCode`.

## How verified (commands + result)
- Repo `./gradlew :core:domain:test` NOT runnable here: root build needs AGP from Google Maven (blocked). Verified in isolated scratch project instead:
  - `/tmp/domain-verify/` = `settings.gradle.kts` (mavenCentral + gradlePluginPortal), root `build.gradle.kts` (only kotlin-jvm / kotlin-serialization `apply false`), copies of `gradle/libs.versions.toml` and `core/domain/` (unchanged from repo).
  - `cd /tmp/domain-verify && /opt/gradle/bin/gradle :core:domain:cleanTest :core:domain:test --no-daemon --console=plain` → BUILD SUCCESSFUL.
  - Test counts (JUnit XML): ChartOfAccountsTest 6, JournalEntryTest 5, MoneyTest 1, PostingEngineTest 9, PostingFuzzTest 1 (5000 iterations), TransactionIntentJsonTest 4. Total 26, 0 failures.
- `grep -rnE "Float|Double" core/domain/src/main` → no matches.
- `grep -rn "android" core/domain/src` → no matches (only removed manifest).
- Maven Central returned HTTP 429 at times; retried with backoff. Not a build issue.

## Open issues / risks
1. BANK_TRANSFER bank→bank (§8 row 3: two different bank معین) NOT implemented. Intent has no second bank field. Needs decision: add `to_bank_account`/`from` field, or drop row.
2. REVERSAL not reachable from intent; via `reverse()` only. Confirm OK.
3. Document numbering (per fiscal year, reversal gets new number) not implemented. T04.
4. Party معین code ranges (1301..1349 / 2101..2199) are an engine-side constraint. T04 must assign within them.
5. Expense category set is fixed (v1). Custom categories not supported.
6. Chart/rules still pending accountant review (T02 open issues).
7. Repo Gradle build (`./gradlew :core:domain:test`) still unverified until CI (T01c) runs with Google Maven access.

## Next task must know
- T04 (Room): implement `PartyAccountResolver` (find-or-create معین, key=`partyKey`, store displayName, codes in `PartyKind.codes`). Persist `JournalEntry` (immutable, never update/delete). Assign doc numbers per fiscal year. Set `reversalOfId`.
- T06 (backend): emit TransactionIntent JSON exactly per "TransactionIntent JSON" above. Use `type` = one of the 14 ids; never REVERSAL. Put `null` for credit mode. `confidence` integer 0..100. Set `needs_clarification` rather than guessing.
- T07 (NL pipeline): parse with `TransactionIntent.fromJson`, catch `DomainException`/`IllegalArgumentException`/`SerializationException`. Check `needs_clarification` before `PostingEngine.post`.
- Do not add Float/Double to `core/domain`.
