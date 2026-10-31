# Handout T10b — manual-journal-editor
Status: partial (CI green on 9db5ab0 = run 41197201498; 2 PARTIAL rows, see Open issues)

## Summary
- Free-form «سند دستی» editor: Jalali date text, memo, 2..50 lines, postable account picker (incl. party معین, find-or-create), Dr/Cr side, toman amount, live totals + difference, save gated on balanced + valid + open fiscal year.
- Commits via `LedgerRepository.commit` with `TemplateId.MANUAL`. Nothing persisted until save. Party معین created only inside save, on Dispatchers.IO.
- FIX ROUND 1: run 41196644484 (50061c3) red on two compile/test issues. Both fixed. Run 41197201498 (9db5ab0) green.
- Audit: 24 MET, 2 PARTIAL, 0 NOT MET (table below).

## Root causes fixed in FIX ROUND 1
- Run 41196644484, step «Lint, test, assemble debug»: `e: ManualJournalScreenTest.kt:4:33 Unresolved reference 'assertExists'`. The import `androidx.compose.ui.test.assertExists` does not resolve against compose BOM 2025.10.00. Fix (9f3ae0d): switched to `assertIsDisplayed()` (already used in green `EntryScreenTest`). Three call sites changed.
- Run 41197201498 (9f3ae0d): compile OK, one test failed: `ManualJournalViewModelTest.newCustomerIsCreatedOnlyWhenTheDocumentIsSaved` (bare `assertTrue`, no message). Cause: `chart_of_accounts.json` seeds sample customer `1301 مشتری (نمونه)` with parent `1300`. The test asserted "no account with parent 1300 before save" (always false) and `single { parent == "1300" }` after save (would throw on 2 matches). Fix (9db5ab0): match party by `nameFa == "علی رضایی"`, assert `parent == "1300"`. Production code unchanged. Test intent kept.
- No tests skipped, ignored or weakened (grep: no `@Ignore`, `assume*`, `@Disabled` in feature/ledger, core/domain, core/persian tests).
- KSP schema race (T10 handout) did NOT reappear in these runs.

## Files created/changed (T10b total, 50061c3 + fix commits 9f3ae0d, 9db5ab0)
- `core/domain/.../ManualJournal.kt`: `ManualAccount`, `ManualLine`, `ManualIssue`, `ManualCheck`, `ManualJournal` (check/build/postableAccounts/tomanToRial/rialToWholeToman/partyDisplayName). Lines 9-115.
- `core/domain/.../TransactionIntent.kt`: `TemplateId.MANUAL` added (documented as never an intent).
- `core/domain/.../PostingEngine.kt`: `TemplateId.MANUAL -> throw DomainException(...)` (rejects MANUAL as intent).
- `core/persian/.../TomanInput.kt`: `parse` (Persian or Latin digits, separators ignored).
- `feature/ledger/.../ManualJournalViewModel.kt` (169 lines), `ManualJournalState.kt` (161), `ManualJournalScreen.kt` (396).
- `feature/ledger/.../JournalListScreen.kt`: «سند دستی» entry (line 93).
- `feature/entry/.../ConfirmationCard.kt` (line 164) + `EntryScreen.kt`: «ویرایش به صورت سند» on unconfirmed draft.
- `app/.../AppShell.kt` lines 41-60: `manualOpen` state, `openManual(seed)`, `ManualJournalRoute`. Back/close return to prior tab.
- Tests: `core/domain/.../ManualJournalTest.kt`, `PostingEngineTest.kt` (MANUAL rejection line 59-66), `PostingFuzzTest.kt` (excludes MANUAL), `core/persian/.../TomanInputTest.kt`, `feature/ledger/.../ManualJournalViewModelTest.kt`, `ManualJournalScreenTest.kt`.
- `tasks/T10b-manual-journal-editor.md`: Status partial, FIX ROUND 1 section.

## Public interfaces / contracts
- `ManualJournal.check(lines: List<ManualLine>, accounts: List<Account>): ManualCheck` (`issues: Set<ManualIssue>`, `debitRial`, `creditRial`, `differenceRial`, `isValid`).
- `ManualJournal.build(id, date, memo, lines, accounts): JournalEntry` throws `DomainException` unless valid and every line is `ManualAccount.Existing`. Output `templateId = MANUAL`.
- `ManualJournalViewModel(ledger: LedgerRepository, parties: PartyRepository, clock: Clock)`; `start(seed: JournalEntry?)`; `onAction(ManualAction)`; `ui: StateFlow<ManualJournalUi>`.
- `ManualAction`: SetDate, SetMemo, AddLine, RemoveLine, SetSide, SetAmount, OpenPicker, ClosePicker, SetQuery, PickAccount, PickNewParty(kind), Save.
- `ManualJournalUi.canSave = check.isValid && dateValid && dateYearOpen && !saving && savedDocNumber == null`.
- Errors (Persian, `feature/ledger/src/main/res/values/strings.xml` 91-102): `manual_issue_*` per `ManualIssue`, `manual_error_save`, `manual_error_party`.

## Validation rules
- Lines: 2..50 (`MAX_LINES`). Amount: whole toman 1..10^14 toman (`MAX_LINE_RIAL`=10^15 rial), stored as rial Long. Input is toman only.
- Balanced: sum debit == sum credit and > 0 (`ManualJournal.kt:83`). `JournalEntry` init re-checks.
- Account: must exist, not گروه, no children (`postableAccounts`, `ManualJournal.kt:59-62`). Same rule as `ChartOfAccounts.requirePostable`, reimplemented (see open issues). Repo `commitInTx` re-checks `isPostable` (`LedgerRepository.kt:179`).
- Party name: `partyDisplayName` trims and collapses spaces; blank rejected.
- Date: `parseJalaliInput`; year must be in `openYears` (`ManualJournalState.kt:130`). Repo `openFiscalYear` re-checks (`LedgerRepository.kt:169`).

## Audit (acceptance + binding requirements)
Status key: MET / PARTIAL / NOT MET. "Device" = needs user check on phone (checklist below).

| ID | Requirement | Status | Evidence |
|---|---|---|---|
| A1 | Works with backend unset and airplane mode | MET (code), device | No network/backend call in manual path (grep `ManualJournal*`, `ManualJournal.kt`). Room only. |
| A2 | Unbalanced/invalid never committed (VM + repo) | MET | `ManualJournalState.kt:73`, `ViewModel.kt:117` guard, `ManualJournal.kt:99` build throws, `LedgerRepository.kt:167-182`. Test `unbalancedDocumentCannotBeSaved`. |
| A3 | No Float/Double for money | MET | grep: none in new files. Money `Long` rial. |
| A4 | Persian digits in UI, Latin in DB | MET | `ManualJournalState.kt:157` `PersianDigits.toPersian`; `TomanInput.parse` accepts both (`TomanInputTest`). |
| A5 | Touch targets >= 48dp | MET | `ManualJournalScreen.kt` `heightIn(min = 48.dp)` at 108,145,187,243,252,258,320,337,345; 56dp at 370. Remove-row `IconButton` (237) M3 default 48dp. |
| A6 | RTL | MET (code) | No LTR override in screen; inherits fa locale from app. Device check. |
| A7 | CI green | MET | Run 41197201498 success (9db5ab0). |
| A8 | No tests skipped | MET | grep: no `@Ignore`/`assume`/`@Disabled`. |
| A9 | Max pushes | MET | This round 2 pushes (9f3ae0d, 9db5ab0) + docs push = 3 of 6 used on branch for this round. |
| A10 | Commit prefix `T10b:` | MET | all fix commits. |
| D1 | Date: Jalali picker from `JalaliDate` | PARTIAL | Text field, Jalali format, validated (`ManualJournalScreen.kt:120-121`). No calendar/picker UI. |
| D2 | Account picker over postable accounts incl. party معین, via search | MET | `pickerAccounts` (`ManualJournalState.kt:114-125`), `LedgerSearch.normalize`. Test `pickerListsOnlyPostableAccounts`. |
| D3 | Side Dr/Cr, amount toman/rial per setting | PARTIAL | Side: yes. Amount: toman only; no rial mode; display via `MoneyFormat(TOMAN)` (`ManualJournalScreenTest.kt:35`). |
| D4 | Live totals + difference indicator | MET | `ManualJournalScreen.kt` liveRegion (171,198,210). Test `unbalancedDocumentShowsDifferenceAndSaveIsDisabled`. |
| D5 | «افزودن سطر», delete row | MET | `AddLine`, `RemoveLine`; test `addLineButtonRecordsAddLineAction`. |
| D6 | Save disabled until Dr==Cr>0 and lines valid | MET | `canSave`; test `balancedDocumentEnablesSaveAndRecordsTheAction`. |
| D7 | Reuse JournalEntry rules, requirePostable, fiscal-year | MET | JournalEntry init; postable rule duplicated (open issue). Fiscal year: `dateInUnopenedFiscalYearBlocksSave`. |
| D8 | Persian error messages | MET | strings 91-102. |
| D9 | Commit via `LedgerRepository.commit`, MANUAL id, undo = reverse | MET | `ViewModel.kt:157`; `TemplateId.MANUAL`; reversal via existing `reverse` (unchanged). |
| D10 | Entry points: journal tab, draft «ویرایش به صورت سند» | MET | `JournalListScreen.kt:93`, `ConfirmationCard.kt:164`, `AppShell.kt:51-60`. |
| D11 | Persist nothing until save; draft prefill | MET | `ViewModel.kt:131-163` only write path; test `draftPrefillKeepsLinesDateAndMemoAndIsNotPersisted`, pre-save check at `ManualJournalViewModelTest.kt:150`. |
| D12 | Party معین find-or-create via PartyRepository, off main thread | MET | `ViewModel.kt:120` IO, `:139` `parties.resolve`; `PartyRepository.kt:21-50`. Test `newCustomerIsCreatedOnlyWhenTheDocumentIsSaved`. |
| D13 | Tests: VM balanced/unbalanced/postable/fiscal/toman/double-submit; UI add/diff/save; trial balance | MET | VM tests lines 100-217; double save `:100-106`; `balancedDocumentIsSavedOnce...` asserts trial debit==credit; Screen tests 3. |
| B1 | MANUAL never an intent: PostingEngine rejects | MET | `PostingEngine.kt` throw; `PostingEngineTest.kt:59-66`. |
| B2 | MANUAL rejected as LLM intent | MET | Backend schema `backend/schema/*.json` has no MANUAL (grep). Fuzz excludes MANUAL (`PostingFuzzTest.kt:20`). |
| B3 | 14 template ids and serializer names unchanged | MET | Only MANUAL added (`TransactionIntent.kt`). |

Count: 24 MET, 2 PARTIAL (D1, D3), 0 NOT MET (26 rows).

## Design critique (code level, design:design-critique)
- Good: one screen state (`ManualJournalUi`) derived from draft; check text and save gate explicit; blank draft starts with Dr and Cr rows.
- Gap: date is free text only (D1). Users type `۱۴۰۴/۱۰/۲۰`; a picker would reduce errors.
- Gap: amount input always toman; no rial mode although the app has a display setting (D3).
- Gap: long account list in picker uses plain list; no grouping by تنظیم سطح. Acceptable for v1.
- Copy: Persian error strings are short and say what to do («سند ثبت نمی‌شود», «چیزی ثبت نشده است»). ZWNJ used in «نمی‌شود», «طرف‌حساب».

## Accessibility (code level, design:accessibility-review)
- Headings: `semantics { heading() }` at `ManualJournalScreen.kt:106,316`.
- Live regions: difference/status polite (`:171,198,210`).
- Remove-row button: `contentDescription` (`:237`).
- Amount fields: `KeyboardType.Number` (`:127,275`).
- Gaps: no explicit contentDescription on account-row tap target (label is text, acceptable); date field has no supporting text describing format. Not blocking.

## Open issues / risks
- PARTIAL D1: no Jalali calendar picker. Fix = picker dialog (year/month/day) using `JalaliDate`. New Compose UI; compile only in CI. Not done this round (not a cheap fix).
- PARTIAL D3: no rial input mode. Fix = setting-aware input unit in `ManualLineDraft.rial`. Not done.
- D7 duplication: `ManualJournal.postableAccounts` duplicates `ChartOfAccounts.requirePostable` rule. Same semantics; cleanup candidate.
- Doc cleanup: `ManualJournal.kt:54-58` has an orphan KDoc above `postableAccounts` (two KDoc blocks stacked). Cosmetic.
- `LedgerScreenTest.kt` calls `.assertExists()` with no import; it compiled in CI (green), cause unverified. Left unchanged.
- A1/A6 need device check.

## Manual device checklist (user)
1. Install latest APK from Releases (this branch's green run).
2. Airplane mode ON. Journal tab → «سند دستی». Open screen: no crash.
3. Record a 3-line document: two debits + one credit with equal totals. Save enabled only when balanced.
4. Enter unbalanced amounts: difference shown in Persian digits, save disabled.
5. Pick account → search «علی» → «ایجاد طرف‌حساب» → save. Check new معین appears under حساب‌های دریافتنی.
6. Journal list: document shows «سند دستی». Reverse it («ابطال»), check the reversal doc.
7. Draft card → «ویرایش به صورت سند» → lines prefilled, nothing saved until tap.

## Next task must know
- Status partial. Do not start dependants (T11) as "done" on this basis without the D1/D3 decision.
- Root causes of FIX ROUND 1 were test/compile-level only. Production logic unchanged.
- Pushes this round: 9f3ae0d (compile fix), 9db5ab0 (test fix), docs push (final).
