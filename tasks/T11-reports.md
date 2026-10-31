# T11 — reports
Status: done (accepted by main agent; A3 PDF render verification -> T13 emulator job; A5 unit setting -> T12)
Depends: T10
Read first: CLAUDE.md, handouts/T10-ledger-ui.md
Skills to invoke: persian-writing:persian-writing, design:design-system, design:design-critique

## Goal
- Trial balance, journal, and ledger reports. PDF and CSV export with correct Persian RTL in PDF.

## Deliverables (files)
- `feature/reports/src/main/kotlin/.../TrialBalance.kt`, `JournalReport.kt`, `LedgerReport.kt`
- `feature/reports/src/main/kotlin/.../export/PdfExporter.kt`, `CsvExporter.kt`
- Tests and golden files in `feature/reports/src/test/`
- CSV digit style: default Latin digits, UTF-8 with BOM. Record choice in handout.

## Acceptance criteria
- [ ] Trial balance: total debit == total credit. Test with a fixture of >= 100 entries.
- [ ] Journal and ledger reports for a chosen Jalali date range.
- [ ] PDF has correct RTL Persian text (checked by text extraction or golden image).
- [ ] CSV opens with correct Persian text (BOM present; test).
- [ ] Amounts shown in the unit set in settings (rial or toman).

## Out of scope
- Backup, settings UI, new accounting rules.

## Verify (commands)
- `./gradlew test assembleDebug`

## Added by main agent (binding)
- Read also `handouts/T10-ledger-ui.md`, `handouts/T10b-manual-journal-editor.md`, `handouts/T04-room-persistence.md` (trialBalance/journal/ledger APIs), `handouts/T05-persian-utils.md`, `handouts/T01d-ci-green.md`, `docs/CI.md`, `docs/DESIGN.md`, `docs/ACCOUNTING_RULES.md`.
- Architecture split so most logic is testable locally in pure JVM: put pure report building in `core/domain` (package `report`): `ReportTable(title, subtitle, columns, rows, totals)`, `TrialBalanceReport`, `JournalReport`, `LedgerReport` builders taking plain data (rows from `LedgerRepository`/`TrialBalance` types mapped to simple data classes so core:domain does NOT import core:data), Jalali formatting via core:persian, amounts as Long rials with a `AmountUnit` (toman/rial) parameter (settings UI is T12: take the unit as a parameter; default toman). Also pure `CsvWriter` (RFC 4180 quoting, UTF-8 BOM, Latin digits, Jalali dates `yyyy/MM/dd` Latin, amounts as integer in the chosen unit, Persian headers). Verify these locally with /opt/gradle/bin/gradle in a scratch project (copy /tmp/domain-verify pattern; report counts). CSV decision (record in handout): Latin digits, amounts integer, BOM, comma separator.
- Android part in `feature/reports`: screens (report type picker, Jalali range + fiscal year selection using the existing date filter components from feature:ledger, on-screen preview table), `PdfExporter` using `android.graphics.pdf.PdfDocument` + Vazirmatn typeface from core:ui resources + `StaticLayout` (RTL text, Persian digits, RTL column order, page header/footer with page numbers, repeating table header), export via `FileProvider` + `ACTION_SEND` share sheet (`cacheDir/reports`), CSV likewise. Add the destination to the app navigation (a «گزارش‌ها» entry reachable from the accounts tab or top bar action; do not add a 4th bottom tab unless trivial).
- PDF verification in CI: Robolectric can run `PdfDocument`; assert page count > 0 and, where text extraction is not feasible, render to Bitmap via `PdfRenderer` only if it works under Robolectric; otherwise assert the layout input (strings reshaping/order) and document the limitation + give the user a manual check (open exported PDF on phone). Be honest in the handout about what is and is not proven.
- Trial balance fixture test with >= 100 entries (generated via PostingEngine fuzz) in the pure part and via Robolectric DB in feature/reports or core/data.
- Push discipline: ONE push per attempt; wait for THAT run to finish before pushing again (poll https://api.github.com/repos/russellzparadox/accounting/actions/runs/<id> with an `until` loop in Bash run_in_background); read the 'Failure summary' step via mcp__github__get_job_logs tail_lines <= 130; max 7 pushes; commit prefix `T11:`. Do NOT end your turn until the last push's CI run has completed AND the handout `handouts/T11-reports.md` is written (audit of every acceptance criterion MET/PARTIAL/NOT MET with file:line, design critique + a11y notes, manual device checklist, final run id); set Status and the README row honestly.
