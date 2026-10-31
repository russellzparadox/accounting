# T08 — entry-ui
Status: todo
Depends: T01b, T04, T07
Read first: CLAUDE.md, handouts/T01b-design-system.md, handouts/T04-room-persistence.md, handouts/T07-nl-pipeline.md
Skills to invoke: impeccable, ui-ux-pro-max (only if installed; else design:design-system, design:ux-copy, design:accessibility-review), persian-writing:persian-writing

## Goal
- Chat-style entry screen. Editable confirmation card. Commit and undo. Follows `docs/DESIGN.md`.

## Deliverables (files)
- `feature/entry/src/main/kotlin/.../EntryScreen.kt`, `EntryViewModel.kt`
- `feature/entry/src/main/kotlin/.../ConfirmationCard.kt`, `ChatInputBar.kt`
- `feature/entry/src/test/` ViewModel tests

## Acceptance criteria
- [ ] Chat layout, RTL. User input and assistant card shown as messages.
- [ ] Confirmation card is editable: amount, date, accounts, lines. Dr and Cr totals shown.
- [ ] Commit disabled while unbalanced. Commit writes via T04 repo.
- [ ] Undo creates a reversing entry. No delete. Test.
- [ ] Touch targets >= 48dp. Copy and colors match `docs/DESIGN.md`.
- [ ] ViewModel states tested: idle, loading, clarification, error, committed.
- [ ] Compose preview for each state.

## Out of scope
- Voice input (T09), ledger screens (T10), settings.

## Verify (commands)
- `./gradlew test assembleDebug`
