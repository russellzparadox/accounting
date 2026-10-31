# Handout T08 — entry-ui
Status: done

## Summary
- Chat-style entry screen (RTL) on `EntryViewModel` (T08a). Draft card, clarification, error, committed/undo, manual sheet, backend config dialog.
- Final green run: android run 41190861220 on head 40b017b (build success, publish success).
- Fix round 1 root causes (all in test code, no app code change):
  - Robolectric default window 320x470dp is shorter than the confirmation card. Totals and Confirm were off-screen. Fixed with `@Config(qualifiers = "w411dp-h1200dp")` on `EntryScreenTest`.
  - `entry_title` and `confirm_action` are both `ثبت تراکنش`. Plain text query matched 2 nodes and `waitUntil` passed on the heading. Fixed by matching the button with `hasText(...) and hasClickAction()`.
  - Bad import `androidx.compose.ui.test.onAllNodes` (member of the compose rule). Removed.

## Files created/changed
- `feature/entry/src/main/kotlin/com/example/accounting/feature/entry/`: `EntryScreen.kt` (`EntryRoute`, `EntryScreen`, chat list, bubbles, `ChatCard`), `ConfirmationCard.kt` (draft card, edit amount dialog, `parseTomanInput`), `ChatInputBar.kt`, `ManualEntrySheet.kt`, `BackendConfigDialog.kt`, `EntryPreviews.kt`.
- `feature/entry/src/test/kotlin/.../EntryScreenTest.kt`: Compose UI tests (Robolectric). Fix round changes only.
- `feature/entry/src/main/res/values/strings.xml`: 84 strings (Persian).
- `app/src/main/kotlin/com/example/accounting/MainActivity.kt`: `@AndroidEntryPoint`, `enableEdgeToEdge()`, `AccountingTheme { EntryRoute() }`.
- `tasks/T08-entry-ui.md`: fix-round notes, Status done.

## Public interfaces / contracts
- `EntryRoute(modifier)`: Hilt entry point. `hiltViewModel<EntryViewModel>()`, collects `state`, forwards `onAction`. Only composable launched from `MainActivity`.
- `EntryScreen(state: EntryUiState, onAction: (EntryAction) -> Unit, modifier, accountName: (String) -> String)`: stateless apart from input text, manual sheet and config dialog flags.
- `ConfirmationCard(card: ChatItem.DraftCard, onAction, accountName, modifier)`: amount hero, date, reasons, lines, Dr/Cr totals, confirm/edit/cancel. Mismatch shows "use detected" / "use model" buttons.
- Actions used by UI: `Submit`, `AnswerClarification`, `Retry`, `Confirm`, `Discard`, `UseDetectedAmount`, `UseModelAmount`, `EditAmount`, `SaveBackendConfig`, `Undo`, `SubmitManual`. UI never calls `LedgerRepository`.
- Test-only helper: `confirmButton()` = `hasText(confirm_action) and hasClickAction()`. Use this matcher wherever a test targets the Confirm button, because the title has the same text.

## Decisions made (+why)
- Compose tests use a phone-sized window (411x1200dp). Confirmation card is taller than the 470dp Robolectric default. No assertion was relaxed.
- Title and confirm label stay identical (`ثبت تراکنش`). Not renamed in this round (copy change is a design decision). Open issue below.
- Config dialog is shown from `EntryScreen` when VM state has a `NOT_CONFIGURED` error. T12 moves it to Settings.

## How verified (commands + result)
- Local: not possible (Google Maven blocked). CI only.
- Run 41190274372 (6ad85a9): fail. `EntryScreenTest.sendThenConfirmThroughViewModelCommits` (timeout), `draftShowsTotalsAndConfirmDispatches` (not displayed).
- Run 41190532098 (199b493): fail. Window fix worked. Duplicate-node error on `ثبت تراکنش`, plus timeout (first wait satisfied by heading).
- Run 41190737127 (8a20589): fail at compile. `onAllNodes` unresolved import (removed in 40b017b).
- Run 41190861220 (40b017b): success. `build` success, `publish` success.
- `EntryViewModelTest.sodaExampleConfirmsIntoBalancedLedger` covers the same draft → commit path at VM level.

## Design review (code level, fix round)
- Not run in this fix round: `design:design-critique` and `design:accessibility-review` were not invoked. Only the CI fixes were in scope.
- Checked by reading code: touch targets `heightIn(min = 48.dp)` on buttons and line rows; `start`/`end` used for alignment; `contentDescription` set on send and disabled mic; `liveRegion` on draft card and error bubble.
- Open a11y/UX items (not fixed):
  - Title and Confirm label identical (`ثبت تراکنش`). Screen reader users hear the same label twice. Consider a different title (e.g. `ورود تراکنش`) or a more specific button label.
  - Confirm card is a single tall LazyColumn item. At 200% font scale it must be scrolled to reach buttons. Re-check on device.
  - Mic button is a disabled placeholder (T09 wires it).

## Open issues / risks
- Party find-or-create happens at draft time (inherited from T08a). An unconfirmed credit draft can create a party account.
- Compose UI tests run on Robolectric only. Not checked on a device or emulator.
- Node 20 action deprecation warnings. Out of scope.

## Next task must know
- `feature:entry` has Compose now (`kotlin.compose` plugin + BOM). Keep the BOM at 2025.10.00 and the Typography named-style rule from T01d.
- For any new Compose test on the entry screen, use a phone-sized qualifier (see `EntryScreenTest` class doc).
- The Confirm button and the title share a label. Match by `confirmButton()` in tests.
- Config dialog and manual sheet are in `EntryScreen`. T12 must move config to Settings and keep `EntryAction.SaveBackendConfig` as the write path.
