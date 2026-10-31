# T08 — entry-ui
Status: todo
Depends: T01b, T08a
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

## Added by main agent
- Depends on T08a state layer (`EntryViewModel`); UI must only call its actions. Read `handouts/T08a-entry-viewmodel.md` and `docs/DESIGN.md`.
- Screens: chat-style entry (message list + input bar + send), `DraftCard` (editable lines view: account name, Dr/Cr, amounts in toman/rial with Persian digits; confirm/discard/edit amount; mismatch banner with two choices), clarification bubble with inline answer field, error bubble with retry/manual, committed bubble with "undo" (reversal), manual-entry bottom sheet (template picker, amount, paid-with, party, category), backend config dialog (URL + token) shown when NotConfigured (T12 will move it to Settings).
- Use design skills (`design:design-critique`, `design:accessibility-review`, `design:ux-copy`) as fallback for impeccable/ui-ux-pro-max; follow `docs/DESIGN.md` tokens (update `Color.kt`/`Type.kt` in core/ui to match). Touch targets >= 48dp, RTL, Vazirmatn, contrast per DESIGN.md.
- Compose UI tests (Robolectric/`createComposeRule`) run by CI: send -> draft card shown; confirm -> committed; RTL layout direction; semantics/contentDescription on mic/send buttons.
- Max 6 pushes; commits prefix `T08:`.
