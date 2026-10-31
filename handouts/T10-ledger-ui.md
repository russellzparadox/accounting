# Handout T10 — ledger-ui
Status: done (fix rounds 1+2 complete; final CI run 41194728898 (run 96) on 0f959e9 = success)

## Summary
- Journal list (newest first, Jalali, Persian digits), account picker (tree/search, hide-zero), account ledger (running balance, totals), document sheet (lines, reversal links both ways, «ابطال (سند اصلاحی)» with confirm). Bottom bar with 3 tabs in RTL order (`AppShell.kt`). No edit/delete on committed docs.
- CI history: 41193328056 on a416f8d = success (pre-fix-round audit). Fix round 1 (340c782) = run 41194425401 FAILED: `:core:data:kspDebugKotlin` Room schema JSON error (not a test failure; feature code never compiled). Fix round 2 (0f959e9) = run 41194728898 (run 96) = success.
- Fix items 1-4 now MET (see audit rows A5, A6, B3, B13). Audit now: 20 MET, 3 PARTIAL, 0 NOT MET.
- Still PARTIAL: B8 (\"anywhere\" scope: unconfirmed draft «ویرایش مبلغ» left allowed, needs user decision), B1 and B14 (process items, not verifiable from repo).

## Files created/changed
- `core/data/.../repo/LedgerRepository.kt` (fiscalYears 139, document 142-147, ledger 103-127, trialBalance 130-134, reverse 87-92).
- `core/data/build.gradle.kts` (fix round 2: lines 28-34, `kspReleaseKotlin` depends on `kspDebugKotlin`).
- `core/data/.../db/Daos.kt` (reversalIdOf 121-122, findWithLines 124-126, observeEntries 128-136, observeLedgerRows 138-148, observeOpeningNet 150-157, trialRows 159-171).
- `app/.../AppShell.kt` (bottom bar 44-56, when-switch 59-63); `MainActivity.kt` (AppShell in setContent); `app/build.gradle.kts` (material icons core); `app/src/main/res/values/strings.xml` (nav_*).
- `feature/ledger/src/main/kotlin/.../ledger/`: `LedgerViewModel.kt`, `LedgerState.kt`, `LedgerSearch.kt`, `DateFilter.kt`, `LedgerFormat.kt`, `LedgerComponents.kt`, `JournalListScreen.kt`, `AccountPicker.kt` (AccountsRoute + picker), `AccountLedgerScreen.kt`, `DocumentDetailSheet.kt`.
- `feature/ledger/src/main/res/values/strings.xml` (66 lines, Persian).
- Tests: `feature/ledger/src/test/.../DateFilterTest.kt`, `LedgerSearchTest.kt`, `LedgerViewModelTest.kt` (Robolectric + in-memory Room), `LedgerScreenTest.kt` (Compose, Robolectric).

## Public interfaces / contracts
- `LedgerViewModel` (`@HiltViewModel`, deps: `LedgerRepository`, `Clock`). Only write: `confirmReverse()` → `ledger.reverse(docId, today)`.
  - State: `filter: StateFlow<LedgerFilter>` (year, preset, custom, fromText/toText, dateError); `journal: StateFlow<List<JournalRow>>`; `journalQuery`; `fiscalYears`; `accounts`; `accountNames: Map<code,nameFa>`; `accountRows: StateFlow<List<AccountRow>>`; `accountQuery`; `hideZeroBalances`; `selectedAccount`; `accountLedger: StateFlow<AccountLedgerUi?>`; `detail: StateFlow<DocumentDetailUi?>`.
  - Actions: `selectYear(Int)`, `selectPreset(DatePreset)`, `onFromTextChange/onToTextChange(String)`, `onJournalQuery`, `onAccountQuery`, `setHideZeroBalances`, `selectAccount(code?)`, `refresh()`, `openDocument(id)`, `closeDocument()`, `askReverse(Boolean)`, `confirmReverse()`.
- Models (`LedgerState.kt`, `LedgerSearch.kt`): `JournalRow(id, date, docNumber, memo, totalRial, reversalOfNumber?, isReversal, isReversed)`; `AccountRow(code, nameFa, level, depth, netRial)`; `AccountLedgerUi(code, name, rows, totalDebit, totalCredit, closing)`; `LedgerRowUi(entryId, date, docNumber, memo, debit, credit, balance)`; `DocumentDetailUi(links, reversing, confirmReverse, error, reversedNumber)` with `canReverse = !isReversal && reversedBy == null`.
- Repository additions: `fiscalYears(): Flow<List<Int>>`; `document(id): DocumentLinks?` (`reversalOf`, `reversedBy`).
- Navigation: `AppShell` (no nav library). `MainTab { ENTRY(ثبت), JOURNAL(دفتر روزنامه), ACCOUNTS(حساب‌ها) }`. `rememberSaveable` tab name. BackHandler: non-entry tab → ENTRY; account ledger → picker (`AccountPicker.kt:53`).
- Screens: `JournalRoute` / `JournalListScreen`; `AccountsRoute` → `AccountPicker` | `AccountLedgerScreen`; `DocumentDetailHost` (ModalBottomSheet + AlertDialog), mounted in both routes.

## Search normalization rules (`LedgerSearch.normalize`, LedgerSearch.kt:36-40)
1. `PersianDigits.toLatin` (۰-۹ and ٠-٩ to 0-9).
2. Remove grouping separators `,` `٬` `،`.
3. `partyKey` (PostingEngine.kt:22-26): ي→ی, ك→ک, remove ZWNJ (U+200C), remove all whitespace.
4. `lowercase()`.
- Text fields matched after normalize on: memo, account code, account name (party names live in account names), with `contains`.
- Amount: digits-only query only (`amountMatches`, LedgerSearch.kt:46-50): equals rial, or whole-toman (rial/10 when divisible).
- Account picker: empty normalized query = tree; otherwise flat match on code or name.
- Not folded (not required by spec): ة vs ه, ئ/ؤ variants.

## Audit (against T10 task file)
Legend: MET / PARTIAL / NOT MET. Evidence = file:line in this repo.

### Acceptance criteria
| # | Criterion | Result | Evidence |
|---|---|---|---|
| A1 | Journal newest first, Jalali dates, Persian digits | MET | LedgerSearch.kt:86 (date desc, docNumber desc); JournalListScreen.kt:135-136 (`jalaliText`, `persianNumber`); JalaliDate.kt:21-22 (PersianDigits in toString); LedgerViewModelTest.kt:86-92; LedgerSearchTest.kt:75-80 |
| A2 | Account ledger running balance; totals match T04 data | MET | LedgerRepository.kt:103-127 (opening via Daos.kt:150-157 across all years; running sum); LedgerViewModel.kt:213-222 (totals, closing); LedgerViewModelTest.kt:141-161 (rows, totals, closing, and net equals `trialBalance` for 1100). Test covers one account, two lines. |
| A3 | Account picker searches by code and Persian name | MET | LedgerSearch.kt:65-69; AccountPicker.kt:116-121 (search field); LedgerSearchTest.kt:120-127 (`مرکزی` and `۱۱۰۲`) |
| A4 | Jalali date-range filter works; text search over description and amount | MET | Range: DateFilter.kt:54-80 (presets), 69-80 (`parseJalaliInput`); LedgerViewModel.kt:76-89, 145-157 (custom range); Daos.kt:136-146 (SQL bounds). Search: LedgerSearch.kt:52-63; LedgerViewModelTest.kt:99-109 (`۵۰۰٬۰۰۰` matches 5,000,000 rial). Gap: no VM-level test of range filtering; DateFilterTest covers parsing and presets only. |
| A5 | Reversals shown as linked pair | MET | Fix item 1. Detail sheet: DocumentDetailSheet.kt:161-182 (both links, via LedgerRepository.kt:142-147). Journal list now uses unfiltered links: `observeReversalLinks` Daos.kt:140 → `LedgerRepository.reversalLinks()` LedgerRepository.kt:153 → `LedgerViewModel` reversalLinks LedgerViewModel.kt:93-97 → `buildJournalRows(docs, query, links)` LedgerSearch.kt:79-98 (no longer derived from `docs`). Tests: LedgerRepositoryTest.kt:162 (window excludes original, link still pairs); LedgerSearchTest.kt:91, 101; LedgerViewModelTest.kt:141 (original Azar, reversal Dey: badge/link hold in each single-month window; sheet `canReverse` false). Final CI run 41194728898 success. |
| A6 | No edit or delete on committed entries; UI test | MET | Fix item 3. No edit/delete action in ledger UI. Assertion helper `assertNoEditOrDeleteAction` LedgerScreenTest.kt:63, scope comment LedgerScreenTest.kt:58-62 (committed docs only; «ویرایش مبلغ» on UNCONFIRMED draft, ConfirmationCard.kt:158, allowed and not checked). Covers journal list + detail (existing), `AccountPicker` LedgerScreenTest.kt:115 `committedAccountPickerHasNoEditOrDelete`, `AccountLedgerScreen` LedgerScreenTest.kt:147 `committedAccountLedgerHasNoEditOrDelete`. Positive controls included. `AppShell` not asserted (no edit surface there). Final CI run 41194728898 success. |
| A7 | ViewModel and filter logic tests pass | MET | LedgerViewModelTest.kt (162 lines), LedgerSearchTest.kt (128), DateFilterTest.kt (56), LedgerScreenTest.kt (138). CI 41193328056 on a416f8d = success; CI step runs `./gradlew lint test assembleDebug` (.github/workflows/android.yml:53). |

### Added by main agent (binding)
| # | Requirement | Result | Evidence |
|---|---|---|---|
| B1 | Read T05, T08a, T09 (skim), T01d, docs/DESIGN.md, docs/CI.md; invoke design:ux-copy and persian-writing | PARTIAL | No evidence in repo (no handout existed before this one). Not verifiable from code. Design skills design:design-critique and design:accessibility-review were invoked in this audit. |
| B2 | Bottom bar, 3 destinations, RTL-correct order, labels | MET | AppShell.kt:27-31 (enum order), 44-56 (NavigationBar, labels); Theme.kt:22 (`LayoutDirection.Rtl`); AndroidManifest.xml:17 (`supportsRtl`); strings.xml nav_* |
| B3 | Keep EntryViewModel state alive across tab switches; no settings in top bar | MET | Fix item 2. Unsent text now in `EntryViewModel`: `inputText` / `input` EntryViewModel.kt:61-67, `onInputChange` :72. Voice text fills it via `onVoiceTranscript` EntryViewModel.kt:81-84 (applies only a higher seq, `lastVoiceSeq`). `EntryRoute` reads it EntryScreen.kt:65 and wires EntryScreen.kt:88 (`input` state hoisted, no `rememberSaveable` input). Comment corrected: AppShell.kt:35-36. Tests: EntryViewModelTest.kt:105 `unsentInputAndVoiceTextAreKeptInTheViewModel` (typed text + re-delivered voice seq); EntryScreenTest.kt:161 `unsentTextSurvivesLeavingTheEntryTab` (leave/re-enter composition). No top-bar settings slot built (correct). Final CI run 41194728898 success. |
| B4 | Data only via LedgerRepository through ViewModels (@HiltViewModel); no DAO in UI | MET | LedgerViewModel.kt:38-41 (`@HiltViewModel`, `LedgerRepository` + `Clock`); grep: no `Dao`/`AppDatabase`/`db.` in `feature/ledger/src/main` or `app/src/main/java` UI (only DI in DataModule.kt) |
| B5 | Fiscal year selector: current Jalali year default, list existing years | MET | LedgerViewModel.kt:44-46 (default `currentYear`), 66 (`fiscalYears`), 125 (`ensureFiscalYear`); LedgerComponents.kt:181-207 (chips incl. selected); LedgerRepository.kt:139 |
| B6 | Reversal «ابطال (سند اصلاحی)» = reverse(docId, today) with confirm; hidden/disabled if reversed or is reversal | MET | DocumentDetailSheet.kt:184-200 (button shown only if `canReverse`; else explanation text); LedgerState.kt:183-187; LedgerViewModel.kt:195-211 (guard + `reverse(id, today)`); confirm dialog DocumentDetailSheet.kt:72-84; LedgerViewModelTest.kt:111-138; LedgerScreenTest.kt:65-103 |
| B7 | Document detail: lines (code+name, Dr/Cr), memo, Jalali date, doc number, reversal link both ways | MET | DocumentDetailSheet.kt:109-148 (title with doc number, jalali date, memo, lines with side word, totals); 161-182 (links both ways) |
| B8 | NO edit, NO delete anywhere; UI test asserts no such node | PARTIAL | Ledger UI clean. Test scope narrower than "anywhere" (see A6). Also, pre-commit draft edit exists in entry feature: `ConfirmationCard.kt:158` («ویرایش مبلغ») and `:220` (`edit_amount_title`), strings entry :80-81. That is on drafts, not committed documents. User decision needed: does "anywhere" include the entry draft? |
| B9 | Search normalization: PersianDigits, partyKey-style, match memo/account/party/amount, علی=علي, ۵۰۰=500 | MET | LedgerSearch.kt:36-40 (uses `partyKey` PostingEngine.kt:22-26 + `PersianDigits.toLatin`), 46-69; LedgerSearchTest.kt:46-72; LedgerViewModelTest.kt:86-109 |
| B10 | Account list: گروه/کل/معین nesting, toman balances with Persian digits, hide-zero toggle | MET | LedgerSearch.kt:107-153 (tree, depth, hide-zero keeps ancestors); AccountPicker.kt:126-142 (toggleable Switch row, role Switch); 157-197 (level labels, balances via `balanceText`); LedgerSearchTest.kt:106-118. Note: group (گروه) rows show no total (AccountPicker.kt:189). |
| B11 | Money/date via T05 utilities only; never Float/Double | MET | LedgerFormat.kt:149-155 (`PersianDigits`, `JalaliDate`, `MoneyFormat`); MoneyFormat.kt:13-30; grep for Float/Double in ledger main and app UI: none. Money is `Long` rial throughout (LedgerState.kt, LedgerRepository.kt). |
| B12 | All user-visible strings Persian in strings.xml | MET | `feature/ledger/src/main/res/values/strings.xml` (66 lines, all Persian); grep for hard-coded `Text("`/`text = "`/`contentDescription = "` in ledger and app UI: none. Nav icons have `contentDescription = null` with visible labels (AppShell.kt:51). |
| B13 | Touch targets ≥ 48dp | MET | Fix item 4. Every FilterChip now has `heightIn(min = 48.dp)`: date presets DateFilter.kt:103, fiscal years LedgerComponents.kt:47. Tests: LedgerScreenTest.kt:187 `dateFilterChipsAreAtLeast48dpTall`, :206 `fiscalYearChipsAreAtLeast48dpTall` (`assertHeightIsAtLeast(48.dp)`). Other explicit ≥48: DocumentDetailSheet.kt:128, 168, 179, 188, 219; AccountPicker.kt:130. Rows 56-64dp (AccountPicker.kt:162, JournalListScreen.kt:125, AccountLedgerScreen.kt:301). Final CI run 41194728898 success. |
| B14 | design:ux-copy, persian-writing, design-system skills used | PARTIAL | No evidence in repo or commit. Not verifiable. |
| B15 | CI-only verification, one push per attempt, wait for run | MET | CI run 41193328056 on a416f8d: success (`gh run view`). |
| B16 | ViewModel and filter tests present | MET | Same as A7. |

Totals (after fix round 2): acceptance 7 MET, 0 PARTIAL. Binding 13 MET, 3 PARTIAL (B1, B8, B14), 0 NOT MET. Overall: 20 MET, 3 PARTIAL, 0 NOT MET.

## Design critique (code-level, design:design-critique)
Static read of the Compose code. No screenshots, so no visual hierarchy or contrast measurement.

| Finding | Severity | Recommendation |
|---|---|---|
| Journal original shows no «معکوس شده» badge, and reversal row shows generic «سند اصلاحی», when the other document is outside the selected date range or fiscal year. The status is misleading for an accountant (LedgerViewModel.kt:84-89, LedgerSearch.kt:82-97). | 🟡 Moderate | Build `numberById` and `reversedIds` from unfiltered year docs, or query reversal links separately. |
| Unsent entry text is lost when switching tabs (EntryScreen.kt:97 rememberSaveable local state). Users draft a sentence, glance at the journal, and come back to an empty field. | 🟡 Moderate | Move the input text into `EntryViewModel` state, or keep the route composed (hidden) rather than removed. Fix the AppShell.kt:35-36 comment. |
| Preset and fiscal-year chips scroll horizontally with no overflow hint (DateFilter.kt:96, LedgerComponents.kt:195). At 200% font scale, later chips (e.g. «ماه قبل», «بازه‌ی دلخواه») are easy to miss. | 🟢 Minor | Add an end fade or wrap to a second row. |
| Group (گروه) rows look like headers but have no total and no tap. Balance-bearing rows use the same visual weight as groups (AccountPicker.kt:157-197). | 🟢 Minor | Decide whether group totals are wanted. If not, keep as is and say so in the spec. |
| Journal list amount shows only the debit total, with no side word. Other lists show «بدهکار/بستانکار». | 🟢 Minor | Consistent with row debit-total semantics. Optional side label. |
| Positive: one consistent row pattern (date line, memo, status, amount), Persian copy, clear confirm dialog («سندهای ثبت‌شده تغییر نمی‌کنند…»), disabled-action explanation text instead of silent hiding (DocumentDetailSheet.kt:192-200). | — | Keep. |

## Accessibility pass (code-level, design:accessibility-review, WCAG 2.1 AA)
Static only. No device, no TalkBack run, no contrast numbers computed.

| # | Criterion | Result | Evidence / note |
|---|---|---|---|
| 1 | 1.4.3 contrast (text) | UNVERIFIED | Uses theme tokens onSurface / onSurfaceVariant / errorContainer (DocumentDetailSheet.kt:99-100, LedgerComponents.kt:233-234). Ratios depend on the theme; not computed here. |
| 2 | 1.4.11 non-text contrast (chips, switch, outline fields) | UNVERIFIED | Material3 defaults. Not checked. |
| 3 | 2.5.5 / Android 48dp targets | PARTIAL | See B13. Chips rely on library default. |
| 4 | 1.3.1 headings | MET | `heading()` on titles: AccountPicker.kt:113, JournalListScreen.kt:82, AccountLedgerScreen.kt:238, DocumentDetailSheet.kt:113, LedgerComponents.kt:256. |
| 5 | 4.1.2 role/state | MET | Switch row `toggleable(role = Role.Switch)` (AccountPicker.kt:131); AlertDialog has title; Button/TextButton standard. |
| 6 | 4.1.3 status messages | MET | Reversed notice and reverse error are `liveRegion = Polite` (DocumentDetailSheet.kt:208, 216). |
| 7 | 3.3.1 error identification | PARTIAL | Date error shows as `isError` plus a text line (DateFilter.kt:115, 130-136). Text is not linked to the field semantics and is not a live region. Minor. |
| 8 | 3.3.2 labels | MET | All fields have labels (`SearchField` label, DateFilter.kt:112, 122). Placeholders are hints only. |
| 9 | 2.4.3 focus/reading order | MET (code) | Column/LazyColumn order follows RTL reading order. Not device-tested. |
| 10 | 2.1.1 keyboard / back | MET (code) | System back: BackHandler for ledger→picker (AccountPicker.kt:53) and tab→ENTRY (AppShell.kt:42). Not keyboard-tested. |
| 11 | 1.1.1 icon text | MET | Back IconButton has `nav_back_cd` (AccountLedgerScreen.kt:227). Nav icons have visible labels, cd null (AppShell.kt:51). |
| 12 | Merged row semantics | MET (code) | `Modifier.clickable` merges descendants, so each row reads as one item. Row role is not Button; acceptable. |
| 13 | 200% text | UNVERIFIED | Rows use min height, so they grow. Chip rows scroll. Not tested. |

## Decisions made (+why)
- Reversal is the only write on the ledger screens. `confirmReverse` is the single write path.
- Search normalization reuses `partyKey` so the ledger and party matching agree.
- Tabs use plain state hoisting (no nav library), matching the binding's "or a simple state-hoisted NavHost".
- Reversal link lookup is repo-global (`reversalIdOf`) so the sheet is always correct. The journal list uses only the loaded window (see Open issues 2).

## How verified (commands + result)
- CI: android run 41193328056 on a416f8d → success (`lint test assembleDebug` includes unit + Robolectric tests).
- Local build/tests: not possible here (Google Maven blocked). CI only.
- Audit: static reading of the code and tests above. No app code or tests changed in this task.
- Greps: `Float|Double` (none in ledger/app), `Dao|AppDatabase|db.` in ledger/app UI (none), hard-coded text (none), `ویرایش|حذف` (ledger: none; entry: `ConfirmationCard`).

## Open issues / risks
Resolved by fix rounds: 2 (A5), 3 (A6 assertions added), 4 (B13), 5 (journal part of 1: unfiltered links), 6-7 not in scope of fix round, 8 partly (tab-switch test added: EntryScreenTest.kt:161).
1. (PARTIAL B8, open) Decide whether the entry-draft «ویرایش مبلغ» (ConfirmationCard.kt:158, 220) falls under "anywhere". Spec reads as committed docs only; code and test currently exclude it (LedgerScreenTest.kt:58-62). User decision needed.
2. (PARTIAL A5, Design #1) Journal list reversal link and «معکوس شده» badge only see docs in the filtered year+date range. Cross-range and cross-fiscal-year links are missing in the list. Fix: derive links from an unfiltered source. Current comment at LedgerSearch.kt:74-75 documents only the fiscal-year limit, not the date-range limit.
3. (PARTIAL B3, Design #2) Unsent text in the entry field is lost on tab switch (EntryScreen.kt:97). The AppShell.kt:35-36 comment overstates. Keep the VM state claim, fix the field, and no test covers this.
4. (PARTIAL B13) FilterChip touch target not explicit. Verify on device; add `minimumInteractiveComponentSize()` if needed.
5. (PARTIAL B1, B14) Process items (reads, ux-copy, persian-writing, design-system skill) have no evidence in the repo.
6. (Minor) Group rows have no total (AccountPicker.kt:189). Product question.
7. (Minor) Date-range error not linked to fields for TalkBack (DateFilter.kt:130-136).
8. (Test gap) No ViewModel or UI test that the journal date range actually filters rows (only parsing and presets tested). No tab-switch test for EntryViewModel state.
9. (Test gap) Account ledger test covers one account only (LedgerViewModelTest.kt:141-161).
10. Real device, TalkBack, 200% font, contrast: unverified (code-level only).

## Manual device checklist (for the user; not testable in CI)
Install the latest APK from Releases (build #91 or newer).
- [ ] Open «ثبت», record an entry via the manual sheet (`ثبت دستی`). Confirm. Check the draft shows `۵۰۰٬۰۰۰ تومان` style amounts.
- [ ] Tap «دفتر روزنامه». The new entry is at the top. Date is Jalali, digits are Persian, row shows «سند شماره ۱» style text.
- [ ] Search the memo word, the amount (`۵۰۰۰۰۰` and the toman form), and an account name. Each finds the entry. Search `علي` (Arabic yeh) finds `علی`.
- [ ] Open the entry. Lines show code + name, بدهکار/بستانکار, totals. No edit or delete button anywhere.
- [ ] Tap «ابطال (سند اصلاحی)». Dialog appears. Cancel does nothing. Confirm posts a reversal dated today. Original shows «معکوس شده». Reversal shows «سند اصلاحی سند شماره ...».
- [ ] Reversal sheet has no reverse button; links go both ways.
- [ ] Switch to «حساب‌ها». Pick an account. Running balance and totals match the journal. Back returns to the picker.
- [ ] Hide-zero switch hides empty accounts. Search by code (e.g. `۱۱۰۰`) and by name.
- [ ] Date filter: «ماه جاری», «ماه قبل», custom `۱۴۰۵/۰۱/۰۱` range. Invalid text shows the error.
- [ ] Bottom bar: order is «ثبت» on the right, «حساب‌ها» on the left. Back from a non-entry tab goes to «ثبت».
- [ ] Type a sentence in «ثبت» (do not send), switch to «دفتر روزنامه», come back. Known issue 3: text is gone. Record what you see.
- [ ] Font scale 200%: chips scroll, rows wrap, buttons tappable.
- [ ] TalkBack: row reads as one item, switch reads its state, reversal notice is announced.

## Next task must know
- `LedgerViewModel` is the only ledger VM. Its one write is `confirmReverse`. Any new ledger write must go through `LedgerRepository` and keep the no-edit rule.
- `LedgerSearch.normalize` is the single search normalization. Reuse it in T11 or T10b, do not fork it.
- T10b (manual journal editor) must not change committed documents. It creates new entries only.
- Open issue 1 (B8 draft-edit scope) needs a user decision before T13 QA. Issues 2-4 resolved in fix rounds. Journal list now uses unfiltered `reversalLinks()`; T11 reports should still use `document()` links, not list rows.
- Build: `core/data/build.gradle.kts:32` runs `kspDebugKotlin` before `kspReleaseKotlin` (shared Room schema dir). Keep this when touching KSP or `org.gradle.parallel`.
- `AppShell` keeps tabs out of composition. Any new tab that holds input must keep its draft in a ViewModel.
