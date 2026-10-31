# Handout T11 — reports
Status: partial (CI green run 41198957224 on 86463d0c; PDF rendering NOT verified by any test; see audit)

## Summary
- Pure report model + builders in `core:domain` (package `report`): `ReportTable`, `TrialBalanceReport`, `JournalReport`, `LedgerReport`, `CsvWriter`. No `core:data` import in `core:domain`.
- `feature:reports`: Reports screen (type chips, fiscal-year chips, shared `DateFilterBar`, preview table), `ReportsViewModel` (Hilt, read-only), CSV export, PDF export (`PdfDocument`, Vazirmatn, RTL), FileProvider share.
- Entry: «گزارش‌ها» action in a top bar on the journal and accounts tabs (`AppShell.kt:73-85`). No 4th tab.
- Pushes to branch: 5 (3c96701, 3f3bcdc, 5648ddef, 86463d0c, docs push). Runs: 41198331478 fail (PDF test), 41198603458 fail (diagnostic), 41198770750 fail (NATIVE graphics still closed), 41198957224 SUCCESS on 86463d0c.
- Earlier WIP commit 7e70e3e (run 41197968229) failed on two wrong test expectations; my commit 3c96701 fixed them. It was superseded.

## Files created/changed
- `core/domain/src/main/kotlin/com/example/accounting/core/domain/report/`
  - `ReportTable.kt`: `ReportTable`, `ReportColumn`, `ReportCell` (Text/Amount/Date/Count), `ReportRange`, `ReportFormat` (display + CSV text).
  - `TrialBalance.kt`: `TrialAccount`, `TrialBalanceReport.build`.
  - `JournalReport.kt`: `JournalDoc`, `JournalDocLine`, `JournalReport.build`.
  - `LedgerReport.kt`: `LedgerHistoryLine`, `LedgerReport.build`.
  - `CsvWriter.kt`: `CsvWriter.write / toUtf8Bytes`.
- `core/domain/src/test/.../report/`: `ReportBuildersTest.kt` (7 tests), `CsvWriterTest.kt` (5 tests).
- `feature/reports/build.gradle.kts` (+ core:data, core:persian, core:ui, feature:ledger, hilt, ksp, tests).
- `feature/reports/src/main/AndroidManifest.xml` (FileProvider, authority `com.example.accounting.reportfiles`); `res/xml/report_file_paths.xml`; `res/values/strings.xml` (16 Persian strings).
- `feature/reports/src/main/kotlin/.../feature/reports/`: `ReportsViewModel.kt`, `ReportsScreen.kt`, `export/ReportFiles.kt`, `export/CsvExporter.kt`, `export/PdfExporter.kt`, `export/PdfLayout.kt` (pure geometry).
- `feature/reports/src/test/.../feature/reports/`: `ReportsViewModelTest.kt` (5), `TrialBalanceFixtureDbTest.kt` (1), `PdfLayoutTest.kt` (4).
- `app/src/main/java/com/example/accounting/AppShell.kt` (reports state, top bar, back handling), `app/src/main/res/values/strings.xml` (`nav_reports`).

## Public interfaces / contracts
```kotlin
// core:domain, package com.example.accounting.core.domain.report
data class ReportTable(title, subtitle, unit: AmountUnit, columns: List<ReportColumn>, rows: List<List<ReportCell>>, totals: List<ReportCell>?, balanced: Boolean = true)
sealed interface ReportCell { Text(value) ; Amount(rial: Long) ; Date(date: LocalDate) ; Count(value: Int) }
data class ReportRange(from: LocalDate?, to: LocalDate?)           // inclusive
object TrialBalanceReport { fun build(fiscalYear: Int, asOf: LocalDate?, accounts: List<TrialAccount>, unit: AmountUnit): ReportTable }
object JournalReport      { fun build(range: ReportRange, docs: List<JournalDoc>, unit: AmountUnit): ReportTable }
object LedgerReport       { fun build(accountCode, accountName, range: ReportRange, history: List<LedgerHistoryLine>, unit): ReportTable }
// history = FULL account history (all dates); opening balance = sum before range.from. Balance = debit - credit.
object CsvWriter          { fun write(table): String  /* BOM + CRLF */ ; fun toUtf8Bytes(table): ByteArray }
// feature:reports
@HiltViewModel class ReportsViewModel(ledger: LedgerRepository, clock: Clock) { val ui: StateFlow<ReportsUi> ; selectKind / selectYear / selectPreset / onFromText / onToText / onAccountCode }
object PdfExporter { fun render(context, table, out: OutputStream): Int /* pages */ ; fun write(context, table, baseName): Uri }
object CsvExporter { fun write(context, table, baseName): Uri ; fun share(context, uri) }
```
Trial balance is cumulative up to `range.to` (repository semantics, `trialBalance(fy, asOf)`); `from` does not filter it.

## Decisions made (+why)
- CSV: UTF-8 with BOM (Excel reads Persian). CRLF records, comma separator (RFC 4180 quoting for `, " CR LF`).
- CSV digits: Latin for generated numbers and dates (`yyyy/MM/dd`, Jalali). User text (memo, names) is written as typed.
- CSV amounts: integer in the chosen unit. DEVIATION: TOMAN with a rial remainder (not divisible by 10) keeps the exact decimal with `.` (`1234567` rial → `123456.7`), never rounded. Reason: rounding would lose money. Rial amounts are always integers.
- Trial balance: accounts with no activity are left out. Unbalanced data is reported (`balanced=false`, warning line), not hidden.
- Ledger opening balance is computed in the pure builder from full history, not read from the repository's running balance (same result, testable without DB).
- Unit: all builders take `AmountUnit`; ViewModel fixes TOMAN until T12 settings (`ReportsViewModel.UNIT`).
- PDF: geometry and page breaks are in pure `PdfLayout` so they run on the JVM. Rendering uses `PdfDocument` directly.
- Share via FileProvider and `ACTION_SEND` chooser; files in `cacheDir/reports` only.
- Ledger account is typed as a code (no picker). The existing `AccountPicker` is `internal` to `feature:ledger`. Reuse would need a visibility change (out of scope).

## How verified (commands + result)
- Local, pure JVM (scratch project `/tmp/t11-verify`, copy of repo `core/domain` + `core/persian`, repo `gradle/libs.versions.toml`): `/opt/gradle/bin/gradle :core:domain:test :core:persian:test --no-daemon` → BUILD SUCCESSFUL.
  - core:domain: 85 tests, 0 failures (report tests: ReportBuildersTest 7 incl. 150-entry PostingEngine fixture, CsvWriterTest 5).
  - core:persian: 107 tests, 0 failures.
- CI (GitHub `android` workflow, `lint test assembleDebug`):
  - 41198331478 (3c96701): fail. `PdfExporterTest`: `document is closed!`.
  - 41198603458 (3f3bcdc): fail, same, diagnostic stack: `PdfDocument.throwIfClosed:228 < PdfDocument.startPage:116`. The first `startPage` throws, so the document has no native backing under Robolectric.
  - 41198770750 (5648ddef, `@GraphicsMode(NATIVE)`): fail, same.
  - 41198957224 (86463d0c): SUCCESS. Includes `ReportsViewModelTest` 5, `TrialBalanceFixtureDbTest` 1, `PdfLayoutTest` 4, plus all existing module tests.
- The android-reports artifact could not be downloaded (blob host refused by the agent proxy), so per-test counts from CI were not extracted. Green conclusion covers all unit tests.
- Not run anywhere: the Android UI, the PDF output, the share sheet. (Google Maven blocked locally.)

## Audit (against T11 acceptance and binding items)
Legend: MET / PARTIAL / NOT MET. Evidence = file:line.

### Acceptance criteria
| # | Criterion | Result | Evidence |
|---|---|---|---|
| A1 | Trial balance debit == credit, fixture >= 100 entries | MET | ReportBuildersTest.kt:25 (150 entries via PostingEngine, scratch 85/85 pass); TrialBalanceFixtureDbTest.kt:64 (120 entries through Room, CI green 41198957224); TrialBalance.kt:11 |
| A2 | Journal and ledger reports for a chosen Jalali date range | PARTIAL | Logic MET: ReportsViewModelTest.kt:94 (journal range), :106 (ledger opening from earlier history); JournalReport.kt:19; LedgerReport.kt:21. UI/range picking not run on a device. |
| A3 | PDF has correct RTL Persian text (text extraction or golden) | NOT MET | No text extraction or golden image. `PdfDocument` cannot be backed under Robolectric in LEGACY (41198331478) or NATIVE (41198770750) mode, so rendering is unexecuted. Only geometry is tested: PdfLayoutTest.kt:14-21 (RTL column placement), :29-56 (page breaks). Device check required. |
| A4 | CSV opens with correct Persian (BOM present, test) | PARTIAL | BOM + UTF-8 bytes: CsvWriterTest.kt:17. Opening in Excel/Sheets not tested (manual). Quoting of Persian `،` and ASCII `,`: CsvWriterTest.kt:27, :35. |
| A5 | Amounts in the unit set in settings (rial/toman) | PARTIAL | Unit parameter on every builder (ReportTable.kt:49, :54; ReportBuildersTest.kt:115). Settings not built (T12); VM fixed to TOMAN (ReportsViewModel.kt companion `UNIT`). |

### Added by main agent (binding)
| # | Requirement | Result | Evidence |
|---|---|---|---|
| B1 | Read the listed handouts and docs | PARTIAL | Read T10, T10b, T04, T05, T01d, docs/CI.md, docs/DESIGN.md (full). docs/ACCOUNTING_RULES.md: first 150 lines only. |
| B2 | Pure builders in `core:domain` (package `report`), no core:data import; Jalali via core:persian; AmountUnit param | MET | core/domain/.../report/*.kt; `core:domain` imports only core:persian (grep). |
| B3 | Pure `CsvWriter`: RFC 4180, BOM, Latin digits, Jalali dates, Persian headers | MET | CsvWriter.kt:11-30; CsvWriterTest.kt:17, :27, :35, :48, :55 |
| B4 | Screens: type picker, Jalali range + fiscal year via existing date filter, preview | PARTIAL | ReportsScreen.kt (ReportsRoute, ReportPreview); reuses `DateFilterBar`, `rangeFor`, `parseJalaliInput` from feature:ledger. Compiles in CI; not run on a device. |
| B5 | PdfExporter: PdfDocument, Vazirmatn, StaticLayout, RTL columns, header repeat, page numbers | PARTIAL | PdfExporter.kt (render: header repeat, footer «صفحه N»); fonts from core:ui `vazirmatn_regular/bold`. Compiles; not executed by any test. |
| B6 | FileProvider + ACTION_SEND share, cacheDir/reports | PARTIAL | ReportFiles.kt; feature/reports AndroidManifest.xml (provider); res/xml/report_file_paths.xml. Not executed. |
| B7 | Navigation entry «گزارش‌ها» (no 4th tab) | PARTIAL | AppShell.kt:56 (state), :62 (back handler), :73-75 (route), :81-86 (top bar, tab != ENTRY). Not run on a device. |
| B8 | PDF verification in CI (page count; or layout input + documented limitation) | PARTIAL | Layout input verified (PdfLayoutTest.kt); PDF page-count test impossible under Robolectric (see A3). Limitation documented here. Render path not verified. |
| B9 | Trial fixture >= 100 via Robolectric DB | MET | TrialBalanceFixtureDbTest.kt:64 (120 entries, asserts DB debit == credit == posted sum, and report.balanced); CI green. |
| B10 | Push discipline: one push per attempt, wait, max 7, no force, branch only, `T11:` prefix | MET | Pushes: 3c96701, 3f3bcdc, 5648ddef, 86463d0c, docs push. No force-push. No dependabot branch touched. |
| B11 | Handout with honest audit, device checklist, final run id | MET | This file. Final branch run is the run for the docs commit that carries this file (reported in the task reply); the last code-verifying run is 41198957224. |

Totals: acceptance 1 MET (A1), 3 PARTIAL (A2, A4, A5), 1 NOT MET (A3). Binding 5 MET (B2, B3, B9, B10, B11), 6 PARTIAL (B1, B4, B5, B6, B7, B8), 0 NOT MET.

## Design critique (static, code-level only; no screenshots)
Design skills: `persian-writing:persian-writing` was loaded. `design:design-system`, `design:design-critique`, `design:accessibility-review`, `design:ux-copy` were NOT invoked. `impeccable` and `ui-ux-pro-max` are not in the skill list (absent). This section is my own static read, not the skill output.

| Finding | Severity | Recommendation |
|---|---|---|
| Ledger account is typed as a code; no picker or name search (ReportsScreen.kt, ledger section). Accountants must know codes. | 🟡 Moderate | Expose a picker from feature:ledger (make `AccountPicker` public or move it to core:ui). |
| Preview is a non-lazy `Column` inside horizontal scroll (ReportPreview). Large journals (thousands of lines) will be slow and heavy. | 🟡 Moderate | Limit preview rows (e.g. first 200 + note) or move to a lazy table; exports are unaffected. |
| Trial balance is cumulative to range end, so the date range looks partial (subtitle «تا …» shows it). | 🟢 Minor | Keep; label is explicit. Note it in the UX copy review. |
| Preview table has no table semantics for TalkBack (cells read in sequence). | 🟢 Minor | Add `collectionInfo`/row semantics later. |
| Error messages use `liveRegion = Polite` (ReportsScreen.kt message Text). Export failures use a Toast. | — | Positive. Keep. |
| Touch targets: FilterChips and buttons `heightIn(min = 48.dp)`. | — | Positive. Not measured on a device. |

## Accessibility notes (static)
- Titles use `heading()` semantics (ReportsScreen.kt title, preview title).
- Chips and buttons have visible text labels. Icon back button has `report_back_cd`.
- Not verified: contrast (theme tokens), 200% font scale, TalkBack reading order of the preview.

## Known gaps / risks (open issues)
1. PDF rendering is unverified by any automated test (A3, B5, B8). Robolectric cannot back `PdfDocument`. Needs a device check (checklist below) before any user relies on the PDF.
2. CSV formula injection: memo or account names starting with `= + - @` are written as-is. Excel may evaluate them. Not mitigated (would change data).
3. TOMAN CSV can contain decimals (`123456.7`) when the rial amount is not a multiple of 10 (deviation from "integer", documented above).
4. Amount unit is fixed TOMAN until T12 provides the setting.
5. Ledger account code is typed, not picked (see critique).
6. Preview is not lazy (see critique).
7. Export files stay in `cacheDir/reports` and are not deleted (cache is cleared by Android; no cleanup job).
8. A stale WIP commit (7e70e3e) with wrong test expectations was pushed earlier; fixed by 3c96701. Mentioned for history.

## Manual device checklist (not testable in CI)
Install the latest APK from Releases (build number of run 41198957224 or newer).
- [ ] Open «دفتر روزنامه» or «حساب‌ها», tap «گزارش‌ها» in the top bar. Screen opens; back returns to the tab.
- [ ] Trial balance: totals row equals on both sides; «تراز آزمایشی» subtitle shows «سال مالی ۱۴۰۵ · تا …».
- [ ] Journal: pick «این ماه» or type a Jalali range `۱۴۰۵/۰۷/۰۱` to `۱۴۰۵/۰۷/۳۰`. Only documents in range appear. Dates are Jalali, digits Persian.
- [ ] Ledger: type `۱۱۰۰` (صندوق). Opening row shows «مانده اول دوره»; running balance and totals match the account ledger screen.
- [ ] Unknown code `۹۹۹۹` shows «حسابی با این کد پیدا نشد». Empty code shows the required message.
- [ ] CSV: share to Files or Drive, open in Excel or Google Sheets. Persian headers and names are readable (BOM). Numbers are Latin digits. No `###` or broken text.
- [ ] PDF: share, open in a PDF viewer. Check: Persian text joined and correct direction; first column at the right; numbers readable; header repeats on page 2+; footer «صفحه ۱» etc.; long memos wrap. Record any problem with screenshot.
- [ ] Font scale 200%: chips scroll, buttons tappable, preview scrolls sideways.
- [ ] TalkBack: titles announced as headings; error message announced when it appears.

## Next task must know
- `ReportsViewModel.UNIT` is the single amount-unit switch point. T12 settings should replace it.
- Reports are read-only. Any new report writes nothing.
- T13 QA must include the manual PDF check above; no automated PDF rendering test exists.
- `PdfLayout` is the place for pagination changes; add tests there, not Robolectric PDF tests.
- Do not add a Robolectric test that calls `PdfExporter.render`: it fails with `document is closed!` on this stack (3 runs).
