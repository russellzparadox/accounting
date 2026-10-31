# T10 — ledger-ui
Status: todo
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
