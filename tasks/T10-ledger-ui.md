# T10 — ledger-ui
Status: partial
Depends: T04, T08
Read first: CLAUDE.md, handouts/T04-room-persistence.md, handouts/T08-entry-ui.md
Skills to invoke: design:design-system, design:ux-copy, design:accessibility-review

## Goal
- Journal list. Per-account ledger (معین). Account picker. Jalali date filter and search.

## Deliverables (files)
- `feature/ledger/src/main/kotlin/.../JournalListScreen.kt`, `AccountLedgerScreen.kt`, `AccountPicker.kt`, `DateFilter.kt`
- ViewModels and tests in `feature/ledger/src/test/`

## Acceptance criteria
- [ ] Journal list newest first. Jalali dates. Persian digits.
- [ ] Account ledger shows running balance. Totals match T04 data.
- [ ] Account picker searches by code and Persian name.
- [ ] Jalali date-range filter works. Text search over description and amount.
- [ ] Reversals shown as a linked pair.
- [ ] No edit or delete actions on committed entries. UI test.
- [ ] ViewModel and filter logic tests pass.

## Out of scope
- Reports and export (T11), entry screen changes.

## Verify (commands)
- `./gradlew test assembleDebug`

## Added by main agent (binding)
- Read also `handouts/T05-persian-utils.md` (JalaliDate, MoneyFormat), `handouts/T08a-entry-viewmodel.md`, `handouts/T09-voice-input.md` (skim), `handouts/T01d-ci-green.md`, `docs/DESIGN.md`, `docs/CI.md`.
- Navigation shell (app level, Compose Navigation or a simple state-hoisted NavHost; verify `androidx.navigation:navigation-compose` version on CI): bottom bar with 3 destinations: «ثبت» (existing EntryScreen), «دفتر روزنامه» (journal list), «حساب‌ها» (account list -> account ledger). RTL-correct order. Keep `EntryViewModel` state alive across tab switches. A top-bar action area is reserved for T12 settings (do not build settings).
- Data comes only from `LedgerRepository` (`journal(fiscalYear, from, to)`, `ledger(accountCode, from, to)`, `accounts()`, `trialBalance`, `reverse`) via ViewModels (Hilt `@HiltViewModel`); no direct DAO use in UI. Fiscal year selector: current Jalali year by default (`ensureFiscalYear`), list the existing years.
- Document detail (bottom sheet or screen): lines (account code+name, Dr/Cr), memo, Jalali date, doc number, reversal link both ways. Allowed action: «ابطال (سند اصلاحی)» = `reverse(docId, today)` with confirm dialog; button hidden/disabled if already reversed or if the doc is itself a reversal. NO edit, NO delete anywhere (UI test asserts no such node/semantics).
- Search: normalize Persian/Arabic digits and ی/ک variants (`PersianDigits`, partyKey-style normalization) so "علی" matches "علي" and "۵۰۰" matches 500; match memo, account name/code, party name, amount (toman or rial display value).
- Account list: grouped by گروه/کل with معین (party accounts) nested, balances in toman with Persian digits, hide zero-balance toggle.
- Money/date formatting: use T05 utilities only; never Float/Double. Dates stored Gregorian, shown Jalali.
- Verification is CI-only: ONE push per attempt; wait for THAT run to finish before pushing again (poll the public API `https://api.github.com/repos/russellzparadox/accounting/actions/runs/<id>` with an `until` loop in Bash run_in_background); read the 'Failure summary' step via mcp__github__get_job_logs tail_lines <= 130. Max 7 pushes. Do NOT end your turn until the CI run for your last push has completed AND `handouts/T10-ledger-ui.md` is written (include a code-level design:design-critique + design:accessibility-review pass and the final run id + conclusion). Previous agents stopped early: do not. Each green push publishes an APK release.
