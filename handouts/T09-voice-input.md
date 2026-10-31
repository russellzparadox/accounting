# Handout T09 — voice-input
Status: partial

## Summary
- On-device `SpeechSource` (fa-IR, partial results, API 31+ on-device recognizer) + `CloudSpeechSourceStub` (UNSUPPORTED). Mic button replaces the T08 `به‌زودی` slot in the input row.
- Voice fills the input field only. Never submits. Send stays the existing action.
- CI: android run 41191826702 on f18e38d = success. Audit found 0 NOT MET, 3 PARTIAL (see Open issues).

## Files created/changed
- `core/data/src/main/kotlin/com/example/accounting/core/data/speech/`: `SpeechSource.kt`, `AndroidSpeechSource.kt`, `CloudSpeechSourceStub.kt`.
- `core/data/src/test/kotlin/com/example/accounting/core/data/speech/SpeechSourceTest.kt`.
- `app/src/main/java/com/example/accounting/di/DataModule.kt` (binding, lines 65-67).
- `app/src/main/AndroidManifest.xml` (`RECORD_AUDIO` line 5; `<queries>` lines 7-12).
- `feature/entry/src/main/kotlin/com/example/accounting/feature/entry/`: `VoiceViewModel.kt`, `VoiceInput.kt` (permission helpers, `MicPermissionSheet`), `ChatInputBar.kt` (`MicButton`, `VoiceStatus`), `EntryScreen.kt` (ON_STOP, permission launcher, sheets).
- `feature/entry/src/main/res/values/strings.xml` (mic 54-56, voice errors 59-65, permission sheet 68-73; `input_mic_soon` removed).
- `feature/entry/src/test/.../FakeSpeechSource.kt`, `VoiceViewModelTest.kt`; `EntryScreenTest.kt` (mic tests); `EntryViewModelTest.kt` (`spokenNumberWordsReachDraftAndNothingCommitsOnItsOwn`).
- `feature/entry/build.gradle.kts` (`activity-compose` for the permission launcher).
- `docs/VOICE.md`.

## Public interfaces / contracts
- `interface SpeechSource` (SpeechSource.kt:26-37): `val events: Flow<SpeechEvent>` (never completes; collect before `start`), `fun start(): SpeechStartResult`, `fun stop()`, `fun release()`. Main thread, one session.
- `sealed interface SpeechEvent` (9-17): `Partial(text)` replaces prior partial; `Final(text)`; `Error(kind: SpeechErrorKind)`.
- `enum SpeechStartResult` (20): `STARTED, BUSY, UNAVAILABLE, NO_PERMISSION, UNSUPPORTED`.
- `enum SpeechErrorKind` (6): `NO_MATCH, UNAVAILABLE, BUSY, NO_PERMISSION, NETWORK, UNSUPPORTED, OTHER`.
- `AndroidSpeechSource(context)`: `recognizerIntent(onDevice)` (companion, 108-115) = fa-IR, partial on, max results 3, `EXTRA_PREFER_OFFLINE` only when not on-device. `mapRecognizerError(code)` (118-126).
- `CloudSpeechSourceStub`: `start()` = UNSUPPORTED, emits nothing.
- `VoiceViewModel` (`@HiltViewModel`, owns one `SpeechSource`): `onAction(VoiceAction.Start(currentText) | VoiceAction.Stop)`; `state: StateFlow<VoiceUiState(listening, error: SpeechErrorKind?, transcript: VoiceTranscript?)>`; `VoiceTranscript(text, seq)`. `onCleared` calls `release()`.
- `EntryScreen(..., voice: VoiceUiState = VoiceUiState(), onVoiceAction: (VoiceAction) -> Unit = {})`. `EntryRoute` injects `VoiceViewModel` via `hiltViewModel()`.
- `DataModule.provideSpeechSource` = unscoped `AndroidSpeechSource`. Cloud swap point.

## UI states
| State | Mic (56dp) | Field | Status line |
|---|---|---|---|
| Idle | `ضبط صدا`, primaryContainer | editable | none |
| Listening | `توقف ضبط`, primary, tap = stop | read-only, partial live-fills | `در حال گوش دادن…` (polite) |
| Error | idle | editable, text kept | error text, error colour (polite) |
| No permission | tap opens rationale sheet | editable | none |
| Permanently denied | tap opens denied sheet | editable | none |
| Busy (draft/parse) | disabled | read-only | none |

## Permission flow (RECORD_AUDIO)
1. Tap mic. Granted (`checkSelfPermission`, live): start with current text as base.
2. Not granted: rationale sheet (`اجازه دسترسی` / `فعلاً نه`). System dialog opens only from the sheet button.
3. Denied, `shouldShowRequestPermissionRationale` still true: sheet closes; next tap shows rationale.
4. Denied and cannot ask again: denied sheet with `باز کردن تنظیمات` → app details settings. Next tap goes to denied sheet until granted.

## Audit (against T09 task file)
Legend: MET / PARTIAL / NOT MET. Evidence = file:line in this repo.

### Acceptance criteria
| # | Criterion | Result | Evidence |
|---|---|---|---|
| A1 | SpeechSource: start, stop, flow of partial/final/error | MET | SpeechSource.kt:9-17, 26-37 |
| A2 | On-device uses fa-IR and partial results | MET | AndroidSpeechSource.kt:111-112; SpeechSourceTest.kt:39-46 |
| A3 | RECORD_AUDIO: rationale, denied state, link to app settings | MET (code). Sheet tested only up to rationale | EntryScreen.kt:114-134; VoiceInput.kt:45-49, 65-103; EntryScreenTest `micWithoutPermission...` |
| A4 | Errors mapped: no match, unavailable, busy, no permission | MET | AndroidSpeechSource.kt:33-34, 118-126; VoiceViewModel.kt:68-74; SpeechSourceTest.kt:56-70; VoiceViewModelTest `startFailuresAreMapped...` |
| A5 | Cloud stub returns explicit unsupported; test | MET | CloudSpeechSourceStub.kt:10; SpeechSourceTest.kt:23-30 |
| A6 | Mic >= 48dp; idle and listening states | MET | ChatInputBar.kt:110-137 (56dp at 130); EntryScreenTest mic idle/`micListeningShowsStopLabel...` |
| A7 | docs/VOICE.md written | MET | docs/VOICE.md |
| A8 | ViewModel tests with fake SpeechSource pass | MET | FakeSpeechSource.kt; VoiceViewModelTest.kt (8 tests); CI 41191826702 success |

### Added by main agent (binding)
| # | Requirement | Result | Evidence |
|---|---|---|---|
| B1 | Read T08a, T01d, T01e, docs/CI.md; invoke design:ux-copy and persian-writing | PARTIAL | No evidence in repo. Previous agent wrote no handout. Not checked by this audit. |
| B2 | Recognizer choice: on-device on API 31+ if available, else default + PREFER_OFFLINE; fa-IR, partial on, max 3 | MET | AndroidSpeechSource.kt:52-60, 108-115; SpeechSourceTest.kt:39-53 |
| B3 | `isRecognitionAvailable` check; `<queries>` for RecognitionService | MET | AndroidSpeechSource.kt:33; AndroidManifest.xml:7-12 |
| B4 | Create/destroy on main thread; release in onCleared; one session (BUSY) | MET | VoiceViewModel.kt:95-97; AndroidSpeechSource.kt:34, 46-50; EntryScreen.kt:65-73 (ON_STOP → stop) |
| B5 | Voice never auto-submits | MET | VoiceViewModel.kt has no submit path (78-87); EntryScreen.kt:105-109 only sets input; EntryViewModelTest `spokenNumberWords...` asserts nothing commits |
| B6 | Partial live-fills field; final stays; send is existing action | MET | VoiceViewModel.kt:78-93; EntryScreen.kt:106-109, 147; VoiceViewModelTest `finalTextStaysInTheField...` |
| B7 | Persian number sentences through the pipeline path, "a few" sentences | PARTIAL | One sentence only: EntryViewModelTest.kt:271-290 (`SPOKEN_TEXT`). Pipeline cross-check uses NumberWordParser (NlPipeline.kt:75). |
| B8 | Permission UI: Persian rationale; RequestPermission launcher; denied → app settings | MET | EntryScreen.kt:114-134; VoiceInput.kt:65-103; strings.xml:68-73 |
| B9 | Text input works in every voice state | PARTIAL | Field is read-only while listening: ChatInputBar.kt:77 (`readOnly = busy || listening`). VOICE.md documents this as a choice. Conflicts with the wording. |
| B10 | Manual device checklist for user (fa-IR may need Google app / offline pack / internet) | MET | docs/VOICE.md "Manual device checklist"; repeated below |
| B11 | Replace the disabled `به‌زودی` mic slot, no second button | MET | Single MicButton in row: ChatInputBar.kt:70; `input_mic_soon` and `input_mic_disabled_cd` removed; no refs (grep) |
| B12 | CI-only verification, one push per attempt, wait for run | MET | CI run 41191826702 on f18e38d: success (checked via gh) |

Totals: acceptance 8 MET. Added-by-main-agent 9 MET, 3 PARTIAL, 0 NOT MET. Overall: 17 MET, 3 PARTIAL, 0 NOT MET.

## Decisions made (+why)
- Recognizer created lazily on first `start`; `onDevice` chosen at creation. Matches binding spec.
- Events are `SharedFlow` with no replay; collection starts in `VoiceViewModel.init` before any `start`.
- Stop on ON_STOP so background does not keep the mic open. Release only in `onCleared`.
- Permission state read live (`checkSelfPermission`) so a grant made in settings is seen on next tap.

## How verified (commands + result)
- Local build/tests: not possible here (Google Maven blocked). CI only.
- CI: android run 41191826702 on f18e38d → success.
- Audit: static reading of the code and tests above. No app code or tests were changed in this task.

## Open issues / risks
- PARTIAL B1: process items (read-also handouts, skill invocation) have no evidence in the repo. Not verifiable from code.
- PARTIAL B7: only one Persian number-words sentence in the pipeline test. Add a few more (e.g. digits-only, mixed, `دو میلیون ریال`) in a follow-up test-only change.
- PARTIAL B9: text input is read-only while listening. Spec says "text input continues to work in every voice state". User decision needed: keep read-only (then amend the spec) or allow typing during listening (then change `ChatInputBar.kt:77`).
- Test gaps (code exists, not tested): denied-permanently sheet and `openAppSettings`; no screen-level test that `Final` does not dispatch `Submit`.
- Known gaps in VOICE.md: listening pulse ring (DESIGN 5.2) not built; if the recognizer never sends result/error after stop, listening stays on; if on-device fa pack is missing there is no fallback to the default recognizer (error maps to UNAVAILABLE/OTHER).
- Real microphone and recognition unverified. Only the user can run the device checklist.

## Manual device checklist (for the user; not testable in CI)
Install the latest APK from Releases. Persian recognition depends on the Google app and its offline fa pack, and may need internet.
- [ ] First mic tap shows the Persian rationale sheet. `اجازه دسترسی` opens the system dialog. Allow. Listening starts.
- [ ] Say `۴ قوطی نوشابه روی هم پانصد هزار تومان خریدم`. Partial text appears in the field while speaking. Final text stays in the field. Nothing is sent.
- [ ] Edit the final text, then send. Draft shows `۵۰۰٬۰۰۰ تومان` (5,000,000 rial).
- [ ] Silence for a few seconds: `صدا واضح نبود` appears. Typing still works.
- [ ] Airplane mode with the default recognizer: network text appears. Typing still works.
- [ ] Offline check: with on-device fa pack installed, speak with airplane mode on. Note whether recognition works.
- [ ] Deny the dialog once, tap mic: rationale again. Deny again (or tick "don't ask again"): denied sheet. `باز کردن تنظیمات` opens app settings. Grant there, return, mic works.
- [ ] Mic busy: tap mic twice quickly or start while another app holds the recognizer. Busy text appears. Typing works.
- [ ] Background the app while listening: listening stops, no crash.
- [ ] Recognizer missing or fa pack not installed: unavailable text. Typing works.
- [ ] Font scale 200%: status line wraps, nothing clipped, mic tappable.
- [ ] TalkBack: mic reads `ضبط صدا`; while listening `توقف ضبط`; status line announced.

## Next task must know
- `SpeechSource` contract is fixed (see Public interfaces). Cloud STT swaps only the binding in `DataModule`.
- Voice never submits. Any new voice feature must keep `Submit` user-triggered.
- Resolve B9 (typing during listening) before T13 QA. It is a product decision.
- T10/T12: the mic and voice state live in `EntryRoute`. Keep `VoiceViewModel` scope unchanged.

## Main agent decisions (post-audit)
- Text field read-only while LISTENING is accepted: partial results live-fill the same field, so concurrent typing would be overwritten. Stop the mic (tap again) to edit. Not a defect.
- Only one number-words voice->pipeline test: extend in T13 QA (add >= 5 spoken-amount sentences incl. "نیم میلیون", "دو و نیم میلیون", mixed digits/words).
- Real-mic behaviour unverified until the user runs the manual checklist above on a device.
