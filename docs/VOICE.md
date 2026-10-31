# VOICE
Status: v1 on-device (T09). Cloud STT not built (stub only).

## API (`core/data/.../speech/`)
- `SpeechSource` (interface): `events: Flow<SpeechEvent>` (never completes), `start(): SpeechStartResult`, `stop()`, `release()`. Main thread only. One session at a time.
- `SpeechEvent`: `Partial(text)` (replaces previous partial), `Final(text)` (first alternative), `Error(kind)`.
- `SpeechStartResult`: `STARTED`, `BUSY`, `UNAVAILABLE`, `NO_PERMISSION`, `UNSUPPORTED`.
- `SpeechErrorKind`: `NO_MATCH`, `UNAVAILABLE`, `BUSY`, `NO_PERMISSION`, `NETWORK`, `UNSUPPORTED`, `OTHER`.
- `AndroidSpeechSource(context)`: on-device recognizer.
  - API 31+ and `isOnDeviceRecognitionAvailable`: `createOnDeviceSpeechRecognizer`. Otherwise `createSpeechRecognizer` with `EXTRA_PREFER_OFFLINE=true`.
  - Always: `EXTRA_LANGUAGE=fa-IR`, `EXTRA_PARTIAL_RESULTS=true`, `EXTRA_MAX_RESULTS=3`, free-form model.
  - Recognizer code mapping: 6/7 no match; 8/10 busy; 9 no permission; 1/2/4/11 network; 12/13 unavailable; rest other.
- `CloudSpeechSourceStub`: `start()` returns `UNSUPPORTED`, emits nothing. Tests: `SpeechSourceTest`.
- Bound in `app/.../di/DataModule.kt` as an unscoped `SpeechSource` (`AndroidSpeechSource`). Each `VoiceViewModel` owns its recognizer and releases it in `onCleared`.

## feature/entry
- `VoiceViewModel`: `onAction(VoiceAction.Start(currentText) | VoiceAction.Stop)`, `state: VoiceUiState(listening, error, transcript)`.
- `transcript` = text typed before speaking + one space + recognized text. It carries a `seq`, so each change is a new value for the UI.
- Never submits. Only the send action submits (`EntryAction.Submit`).
- UI: `ChatInputBar` (mic 56dp in the input row, status line under it), `MicPermissionSheet`, `EntryScreen` (permission launcher, transcript to input).

## States
| State | Mic | Field | Status line |
|---|---|---|---|
| Idle | `ضبط صدا`, primaryContainer | editable | none |
| Listening | `توقف ضبط`, primary; tap stops | read-only, partial text live-fills | `در حال گوش دادن…` (polite) |
| Error | idle | editable, text kept | error text in error color (polite) |
| No permission | tap opens rationale sheet | editable | none |
| Permanently denied | tap opens denied sheet | editable | none |
| Busy (draft/parse running) | disabled | read-only | none |

Error text (`SpeechErrorKind` to string): no match `صدا واضح نبود...`, unavailable, busy, network, no permission, unsupported, other (generic). Strings in `feature/entry/src/main/res/values/strings.xml`.

## Permission flow (RECORD_AUDIO)
1. Tap mic. Already granted: start listening.
2. Not granted: rationale sheet (`اجازه دسترسی` / `فعلاً نه`). The system dialog opens only from the sheet button.
3. Granted: start with the current input text as the base.
4. Denied, and the system still allows asking (`shouldShowRequestPermissionRationale` true): sheet closes. Next tap shows the rationale again.
5. Denied, and the system does not allow asking again (second denial or "don't ask again"): denied sheet with `باز کردن تنظیمات`, which opens app details settings. Next tap goes straight to the denied sheet until permission is granted.
6. Tap while permission is granted in settings: checked live (`checkSelfPermission`), so the stale denied flag is not used.
Manifest: `RECORD_AUDIO` in `app/src/main/AndroidManifest.xml`. Package visibility: `<queries>` for `android.speech.RecognitionService` (Android 11+).

## Input rules
- Partial and final text only fill the field. The user reads it and taps send.
- Field is read-only while listening; send is off while listening. Edits are possible again after the session ends.
- Stopping the mic (tap or app going to the background, `ON_STOP`) asks the recognizer to finish. Listening ends when its final result or error arrives.
- Numbers may come as digits or words (`پانصد هزار تومان`). The normal pipeline (`NumberWordParser`) handles both; tested in `EntryViewModelTest.spokenNumberWordsReachDraftAndNothingCommitsOnItsOwn`.

## Known gaps / open
- The listening pulse ring (DESIGN 5.2, decorative) is not built. The listening state is a colour change plus the status text.
- If the recognizer never sends a result or error after stop, listening stays on until the next event.
- On-device fa-IR may not be installed; then the recognizer returns an error that maps to `UNAVAILABLE` or `OTHER`. There is no automatic fallback to the default recognizer.
- No cloud implementation. Swap the binding in `DataModule` to add one.

## Manual device checklist (not testable in CI)
Persian recognition depends on the Google app and its offline fa pack, and may need internet. Run on a real phone with the CI APK (`docs/CI.md`).
- [ ] First mic tap shows the rationale sheet in Persian. `اجازه دسترسی` opens the system dialog. Allow it. Listening starts.
- [ ] Say `۴ قوطی نوشابه روی هم پانصد هزار تومان خریدم`. Partial text appears in the field while speaking. The final text stays in the field. Nothing is sent.
- [ ] Edit the final text, then send. The draft shows `۵۰۰٬۰۰۰ تومان` (5,000,000 rial).
- [ ] Silence for a few seconds: `صدا واضح نبود` appears. Text input still works.
- [ ] Airplane mode with the default recognizer: network text appears. Typing still works.
- [ ] Deny the dialog once, tap mic: rationale again. Deny again (or tick "don't ask again"): denied sheet. `باز کردن تنظیمات` opens app settings. Grant the permission there, return, mic works.
- [ ] Put the app in the background while listening: listening stops, no crash.
- [ ] Recognizer missing or fa pack not installed: unavailable text. Typing works.
- [ ] Font scale 200%: status line wraps, nothing clipped, mic still tappable.
- [ ] TalkBack: mic reads `ضبط صدا`; while listening `توقف ضبط`; status line announced.
