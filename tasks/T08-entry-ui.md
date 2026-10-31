# T08 — entry-ui
Status: in_progress
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

## Added by main agent (round 2, binding)
- Read `handouts/T08a-entry-viewmodel.md` first (state/action types, DI, rules) and `handouts/T01d-ci-green.md` (Typography gotcha: M3 `Typography` ctor `defaultFontFamily` is internal in BOM 2025.10.00 -> use named styles + copy()).
- T08a REMOVED the Compose plugin from `feature:entry` ("no UI yet, runtime absent"): re-add `kotlin.compose` plugin + Compose BOM, ui, material3, tooling-preview, `androidx.lifecycle:lifecycle-runtime-compose`, `androidx.hilt:hilt-navigation-compose` (verify coordinates/versions exist on Google Maven via CI), and Compose UI-test deps (`ui-test-junit4`, `ui-test-manifest` debug) with Robolectric for `./gradlew test`. Keep versions in `gradle/libs.versions.toml`.
- `app` `MainActivity` hosts `EntryScreen` inside `AccountingTheme` (RTL already forced there). Entry point only; journal/ledger/settings screens are later tasks (add a minimal bottom bar or top-bar actions placeholder only if trivial; do not build T10-T12 screens).
- Money display: toman by default with Persian digits via `MoneyFormat.format` (T05); never Float/Double.
- All strings Persian in resources (reuse T08a keys, add new ones with ZWNJ done right); contentDescription for icon buttons (send, mic placeholder disabled with "به‌زودی" hint, because voice is T09 - reserve the mic button slot in the input bar).
- Backend config dialog: URL + token fields writing via `ConfigStore` (T08a); show when VM state is NotConfigured; masked token field.
- Verification is CI-only: push ONCE per attempt, wait for that run to finish before pushing again (workflow cancels in-flight runs; poll the public API `https://api.github.com/repos/russellzparadox/accounting/actions/runs?branch=claude/dreamy-mccarthy-mczm8c` with `until` loops in Bash run_in_background or sleeps <= 50s), read the 'Failure summary' step via `get_job_logs` tail_lines <= 130. Max 7 pushes. Each green push publishes a release that the user will install on a phone: the final green build must contain the working entry screen.
- Design review: run `design:design-critique` and `design:accessibility-review` on the finished screens (code-level review of the composables/tokens is fine) and record findings + fixes in the handout.
