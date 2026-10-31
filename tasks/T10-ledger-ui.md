# T10 — ledger-ui
Status: done
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

## FIX ROUND 1 (main agent, after audit in handouts/T10-ledger-ui.md; CI was green on 7fa46c5, run 41193882016)
Fix these audit PARTIALs (nothing else; no scope growth):
1. Reversal link/badge must be correct regardless of the loaded date range/fiscal year: resolve `reversalOfId` / "reversed by" via a repository query (add a small DAO/repo method in core:data with a Robolectric test, e.g. `reversalOf(docId)` / `reversedBy(docId)` or load the linked doc by id) instead of scanning only the loaded list (LedgerViewModel.kt:84-89, LedgerSearch.kt:82-97). Test: reverse a doc dated outside the active filter -> badge and link still shown; reverse button disabled.
2. Unsent entry input text must survive tab switches: hoist the input text out of `rememberSaveable` local state (EntryScreen.kt:97) into `EntryViewModel` state (with the voice partial/final text flow that fills it), keep process-death restore via SavedStateHandle if simple. Fix the misleading comment at AppShell.kt:35-36. Test: type text, switch tab, return -> text still there; voice partial text also preserved.
3. Extend the "no edit/delete" UI test to AccountPicker and AccountLedgerScreen (assert no nodes/semantics for edit/delete/«ویرایش سند»/«حذف»). Note: «ویرایش مبلغ» on an UNCONFIRMED entry draft card is allowed (it is not a committed document); add a comment + test name making that explicit.
4. FilterChip touch targets: make >= 48dp explicit (e.g. `Modifier.minimumInteractiveComponentSize()` or height) wherever chips are used in feature/ledger.
Rules: ONE push per attempt; wait for that run to finish before pushing again (poll https://api.github.com/repos/russellzparadox/accounting/actions/runs/<id> with an `until` loop in Bash run_in_background); read 'Failure summary' via mcp__github__get_job_logs tail_lines <= 130; max 5 pushes; never weaken/skip tests; commit prefix `T10:`. Include this uncommitted task-file edit in your first commit. At the end update handouts/T10-ledger-ui.md (audit rows for the 4 items -> MET with evidence, final run id), set Status done only if final run is green and all 4 are MET, update the T10 row in tasks/README.md. Do NOT end your turn before the final run completes and the handout is updated.

## FIX ROUND 2 (main agent)
- Fix round 1 commit 340c782 was RED: CI run 41194425401 (run 94). Read its 'Failure summary' step (mcp__github__get_job_logs on the run's job, tail_lines 130), fix root causes (compile errors or failing tests among the ones added in 340c782), no weakening/skipping tests, no scope growth. Same push discipline (ONE push per attempt, wait for the run to finish, max 4 pushes, prefix `T10:`).
- When the final run on your last push is green: update handouts/T10-ledger-ui.md (audit rows for fix items 1-4 -> MET with file:line evidence, final run id), set Status done, update the T10 row in tasks/README.md. Include this uncommitted task-file edit in your first commit. Do NOT end your turn before the final run completes and docs are updated.
