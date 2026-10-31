# Handout T12b — settings-follow-through
Status: done (code CI green run 41203285636, build #118, on d82d710; docs-commit run = last run on branch, see Final runs)

## Summary
- Amount unit (toman/rial) drives all money display and input via `LocalAmountUnit` (core:ui), provided once in `AppShell`.
- Fiscal year from Settings is the default for journal, accounts, reports; `ensureFiscalYear` at start; commit and reverse open their own date's year lazily (doc 1 of that year).
- Privacy copy corrected (speech = phone's speech service, may use internet). Duplicate backend dialog removed; NOT_CONFIGURED opens Settings.
- FIX ROUND 1: one red test (`ManualJournalViewModelTest.dateInUnopenedFiscalYearIsSavedAndOpensThatYear`) fixed in the test only. Run 41202841940 red, 41203285636 green.

## Files created/changed
- `feature/ledger/src/test/.../ManualJournalViewModelTest.kt` (~L232-248): wait predicate now `!dateYearOpened && canSave`; assertions carry messages (`canSave false, issues=…`, `fiscal years=…`). No expectation changed.
- `tasks/T12b-settings-follow-through.md` (Status done), `tasks/README.md` (T12b row), `handouts/T12b-settings-follow-through.md` (this file).
- Production code: no change in FIX ROUND 1 (the red test was the only failure in the CI summary).

## Public interfaces / contracts
- `LocalAmountUnit: ProvidableCompositionLocal<AmountUnit>` and `amountText(rial: Long): String` (core:ui `money/AmountUnitLocal.kt`). Every money string in UI goes through `amountText`.
- `AmountInput.parseRial(text, unit)` (core:persian) is the only input converter. `MoneyFormat` for display.
- `LedgerRepository.commit` / `reverse` open the fiscal year of the entry date inside their transaction (`openFiscalYear`, LedgerRepository.kt ~L88-92, ~L208-210).
- `ConfigStore` is the only backend config source. The entry screen no longer has its own dialog.

## Decisions (+why)
- One CompositionLocal fed from `SettingsRepository` at `AppShell` (AppShell.kt:67-68) instead of per-screen copies: one mechanism, nothing to forget.
- Lazy year open at commit (not only at start): an NL entry dated in another Jalali year must not fail with "outside fiscal year".
- Test fix (FIX ROUND 1): the old wait `{ !it.dateYearOpened }` is true as soon as the date text lands, but `canSave` also needs `check.isValid`, which needs the accounts flow (loaded async). The assert ran before accounts were in the state. Waiting for `canSave` in the same predicate removes the race. Root cause is INFERRED from code: CI printed only a bare `java.lang.AssertionError` (an `assertTrue` with no message); the failing line could not be seen. Messages added so a future failure is readable. Expectation (saves, doc 1 of 1405, year opened) is the T12b item 2 behaviour and is unchanged.

## How verified (commands + result)
- `mcp__github__get_job_logs` run 41202841940, job `build`, Failure summary: `Execution failed for task ':feature:ledger:testDebugUnitTest'`, `total failed testcases: 1`, no `e:` compile errors.
- Push d82d710 → run 41203285636 (run #118): `conclusion: success`. Only one push was needed in FIX ROUND 1.
- Local Android build/test impossible (Google Maven blocked). Verification = CI only.

## Audit (items 1-5 of tasks/T12b). MET = proven by a CI-run test or read/grep checked by me; PARTIAL = code present but a part is unproven; NOT MET = missing.

| # | Criterion | Result | Evidence (file:line) |
|---|---|---|---|
| 1 | Amount unit everywhere, one mechanism, Long math | MET | Single source: `AppShell.kt:67-68` provides `LocalAmountUnit`; `core/ui/.../money/AmountUnitLocal.kt:13-17`. Uses: `JournalListScreen.kt:175`, `DocumentDetailSheet.kt:142-157`, `AccountLedgerScreen.kt:93-129`, `AccountPicker.kt:204-205` (balances), `ConfirmationCard.kt:63-64,196,228` (draft card), `ManualEntrySheet.kt:73` (quick sheet), `ManualJournalScreen.kt:182-195,247`, search `LedgerSearch.kt:48-65`, reports `ReportsViewModel` (unit from settings). `tomanText` removed (grep: 0 hits). No Float/Double in ledger/entry/money code (grep: 0 hits). |
| 1a | Test: flip changes amounts on journal | MET | `LedgerScreenTest.kt:269` `journalRowAmountIsRialWhenSettingIsRial`, `:293` toman twin |
| 1b | Test: flip changes amounts on ledger | MET | `LedgerScreenTest.kt:317` `accountLedgerTotalsFollowTheSettingsUnit` |
| 1c | Test: flip changes draft card | MET | `EntryScreenTest.kt:207` `draftShowsTotalsInRialWhenSettingIsRial` |
| 1d | Test: 500 in rial posts 500, toman posts 5000 rial (quick sheet) | MET | `EntryScreenTest.kt:221-233` (`manualFormPostsTypedAmountAsRialInRialMode`, `...AsTomanInTomanMode`, `assertManualAmount`) |
| 1e | Test: search by amount reads settings unit | MET | `LedgerViewModelTest.kt:124` `searchByAmountReadsTheQueryInTheSettingsUnit` (+ `:102` toman Persian digits); pure rule `LedgerSearchTest.kt:58` |
| 1f | Account picker balances: unit | PARTIAL (code only) | `AccountPicker.kt:204-205` uses `amountText`; no unit-flip test on the picker. Not in the required test list. |
| 2 | Fiscal year from Settings = default selection | MET | Journal/ledger: `LedgerViewModel.kt:57`; reports: `ReportsViewModel.kt:78`, re-select on Settings change `:91-93`; start open: `AppShell.kt:64-66`; `LedgerViewModel.kt:140-145` |
| 2a | Lazy open at commit, doc 1 of that year | MET | `LedgerRepository.kt` `commitInTx`/`openFiscalYear` (~L88-92, ~L205-210) |
| 2b | Robolectric test: previous-year entry commits as doc 1 of that year | MET | NL path: `EntryViewModelTest.kt:152-176` (1404/12/10 → fiscalYear 1404, docNumber 1). Repo path: `core/data/.../LedgerRepositoryTest.kt:95-107` |
| 2c | Gap-free numbering per year | MET | `LedgerRepositoryTest.kt:67` `docNumbersAreSequentialWithoutGaps`; `:107-108` 1405 keeps its own counter |
| 2d | Reports default-year test | PARTIAL (code only) | `ReportsViewModel.kt:78` reads settings; no test asserts the default selection from settings. |
| 3 | Privacy copy: no "always on-device" claim; required points | MET | `feature/settings/src/main/res/values/strings.xml:72-78`: speech = phone's speech service, may use internet on some phones (`:73`); app gets only recognized text; only submitted text + today's date go to proxy (`:72`); no API key in app, proxy token encrypted (`:75`); manual entry works without backend (`:77`). Grep for "always on-device"/"روی گوشی" claims: none left. Used by `OnboardingScreen.kt:57`. |
| 4 | Single backend config UI; NOT_CONFIGURED → Settings | MET | `BackendConfigDialog.kt` deleted (no file in feature/entry). `EntryScreen.kt:103-104` (NOT_CONFIGURED calls `onOpenBackendSettings`). Ported test: `EntryScreenTest.kt:251-…` (was dialog coverage), `EntryViewModelTest.kt:246`. One config source: `ConfigStore` (core:data). |
| 5 | Safety copy location + recovery documented (no UI) | MET (doc) | Location: `feature/settings/.../backup/BackupManager.kt:44-45` (`dbFile = context.getDatabasePath("accounting.db")`), `:119` copy written, `:152` `SAFETY_NAME = "accounting.db.before-restore"`. So the file is `databases/accounting.db.before-restore` in app-private storage. Help text note in Settings: NOT added (optional, "only if trivial"; not done). |

Count: items 1-5 MET (1 has 2 PARTIAL sub-points that are code-only, not required tests: 1f, 2d). No NOT MET.

## Safety copy: how a user could recover it (no UI)
- Path: app-private `databases/accounting.db.before-restore` (same folder as `accounting.db`). Created by `BackupManager.swap` before the staged file is renamed in. It stays until the next restore overwrites it.
- Debug build only (the APK from `build-<n>` is debug, `debuggable` true): `adb shell run-as com.example.accounting cp databases/accounting.db.before-restore /sdcard/Download/` then `adb pull /sdcard/Download/accounting.db.before-restore`.
- Release (non-debuggable) builds: `run-as` is refused and there is no in-app way to read it. Rooted device or a file manager with app-data access only. Open issue (see below).

## Manual device checklist (user)
1. Install the APK from run 41203285636 (build #118) or the docs-commit run's `build-<n>`.
2. Settings → unit «ریال». Quick manual sheet (from «ثبت»): type 500 → save. Journal and document detail show 500 ریال. Ledger shows the same rial value. Draft card (NL entry) shows rial totals.
3. Switch back to «تومان». Type 500 in quick sheet → posts 5000 rial (document detail shows 500 تومان).
4. Journal search: type the amount in the current unit (e.g. 500 in toman); the document is found. Switch unit; the same query no longer matches.
5. NL entry (or manual editor) dated in the previous Jalali year (e.g. 1404/12/10) → commits without error; document number 1 of 1404 shown; the 1404 year appears in the Journal year chips.
6. Settings: pick a fiscal year (e.g. 1404). Reports and Journal default to 1404 after restart.
7. Backend not configured (empty URL/token): on «ثبت» tap the NL input → the error action «تنظیمات سرور» opens Settings (no dialog). Manual entry still works.
8. Privacy text (onboarding or Settings → «متن حریم خصوصی»): point 2 mentions the phone's speech service and that it may use internet on some phones. Check the Persian reads correctly.
9. Backup (Settings): restore a backup; confirm the app restarts; the safety copy exists (debug build: `run-as` command above).
10. TalkBack on the account picker and journal rows: amounts read in the chosen unit.

## Open issues / risks
1. Account picker balances (`AccountPicker.kt:204-205`) have no unit-flip test. Code uses the shared formatter, so low risk. Add a test in a later task if wanted.
2. Reports default-year-from-settings (`ReportsViewModel.kt:78`) has no direct test. Covered only by code reading.
3. Settings help text for the safety copy not added (T12b item 5 said «only if trivial»). Recovery is by adb (debug build) only; release builds have no path without root. Consider an in-app «ذخیره نسخه ایمنی» export or delete action in a later task.
4. The FIX ROUND 1 race explanation is inferred from code, not from a seen failing line (CI printed a bare AssertionError). If the test flakes again, the new messages show which condition failed.
5. Privacy copy (item 3) is accurate to the code, but not reviewed by the owner or a lawyer. Needs owner sign-off.
6. Still open from T12: device checks (T12 checklist), KeystoreTokenCipher not run under a test (no AndroidKeyStore in Robolectric).
7. Node 20 deprecation warnings from actions (checkout, setup-java, upload-artifact, setup-gradle). Not in scope.

## Final runs
- Code commit d82d710 (test fix + task file): run 41203285636, run #118, `success`. Jobs: `build` success (lint, test, assembleDebug), `publish` ran for the branch.
- Docs commit (this handout, README row, task status) is pushed after this file. Its run is the last run on the branch; its id is reported in the final reply of the task (the code it builds is identical to d82d710).

## Next task must know
- `LocalAmountUnit` + `amountText` is the only money formatter for UI. New money screens must use it, not `MoneyFormat` with a hardcoded unit.
- `LedgerRepository.commit`/`reverse` open their own fiscal year. Do not add an "outside fiscal year" pre-check in UI code.
- Backend config lives only in Settings. Entry screen links there; do not reintroduce a config dialog.
- Restore path is `BackupManager.restore` only; the safety copy is app-private, see recovery note above.
