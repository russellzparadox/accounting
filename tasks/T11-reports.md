# T11 — reports
Status: todo
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
