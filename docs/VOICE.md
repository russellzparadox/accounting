# VOICE
Status: v1 on-device with one fallback to the system recognizer (T09b). Cloud STT not built (stub only).

## API (`core/data/.../speech/`)
- `SpeechSource` (interface): `events: Flow<SpeechEvent>` (never completes), `start(): SpeechStartResult`, `stop()`, `release()`. Main thread only. One session at a time.
- `SpeechEvent`:
  - `Partial(text)`: replaces the previous partial.
  - `Final(text)`: first alternative.
  - `Error(kind, code: Int?)`: `code` is the platform `SpeechRecognizer` code, null when there is none.
  - `EngineChanged(engine, afterErrorCode)`: a new attempt starts. `afterErrorCode` is set on the fallback. Diagnostics only.
- `SpeechEngine`: `ON_DEVICE`, `SYSTEM`.
- `SpeechStartResult`: `STARTED`, `BUSY`, `UNAVAILABLE` (no recognition service at all), `NO_PERMISSION`, `UNSUPPORTED`.
- `SpeechErrorKind`:
  - `NO_MATCH`: silence, timeout, or empty result.
  - `NO_RECOGNIZER_INSTALLED`: no service. Start result `UNAVAILABLE` maps here.
  - `LANGUAGE_PACK_MISSING`: codes 12 and 13 after the chain is exhausted.
  - `BUSY`, `NO_PERMISSION`, `NETWORK`, `UNSUPPORTED`, `OTHER`.
  - `UNAVAILABLE` was removed in T09b. It hid three different causes behind one message.
- `AndroidSpeechSource(context, factory = AndroidRecognizerFactory(context), hasPermission)`: the chain below. The factory and permission check are injectable, so tests run with a fake recognizer.
- `RecognizerPort` / `RecognizerPortFactory` (`RecognizerPort.kt`): thin wrapper over `SpeechRecognizer`. `AndroidRecognizerFactory` is the production impl. `mainThread` is a Handler-based executor (`Context.getMainExecutor` is API 28, minSdk is 26).
- `mapRecognizerError(code)` (companion):
  - 6/7: `NO_MATCH`.
  - 8/10: `BUSY`.
  - 9: `NO_PERMISSION`.
  - 1/2/4/11: `NETWORK`.
  - 12/13: `LANGUAGE_PACK_MISSING`.
  - rest: `OTHER`.
- `recognizerIntent(onDevice)`: fa-IR, partial results on, max 3 results. `EXTRA_PREFER_OFFLINE` only for the system recognizer.
- `CloudSpeechSourceStub`: `start()` returns `UNSUPPORTED`, emits nothing.
- Bound in `app/.../di/DataModule.kt`. `AndroidSpeechSource` is unscoped (one per `VoiceViewModel`). `SpeechDiagnostics`, `SpeechLanguagePack` (`LanguagePackController`) and `SpeechProbe` are singletons.

## Fallback chain (`AndroidSpeechSource`)
1. `start()`: permission checked first (`NO_PERMISSION`). Then `serviceAvailable()` (no service: `UNAVAILABLE`, no recognizer created). Then busy check.
2. Attempt 1 is `ON_DEVICE` when `onDeviceAvailable()` (API 31+ and `isOnDeviceRecognitionAvailable`). Otherwise `SYSTEM`.
3. Attempt 1 fails with 5 (client), 11 (server disconnected), 12 or 13 (language): attempt 2 is `SYSTEM` (network allowed, no `PREFER_OFFLINE`). The on-device recognizer is destroyed first. Emits `EngineChanged(SYSTEM, code)`.
4. Any other error does not fall back. Neither does a failure of attempt 2. Both emit the typed `Error(kind, code)`.
- At most 2 attempts per session (`MAX_ATTEMPTS`). Each attempt has its own id. Callbacks from a destroyed attempt are ignored.
- Destroy happens before each new attempt and in `release()`.

## feature/entry
- `VoiceViewModel`: `onAction(VoiceAction.Start(currentText) | Stop | DownloadLanguagePack)`. `state: VoiceUiState(listening, error, errorCode, transcript, pack)`.
- `PackUi`: `None`, `Offer` (download button), `Pending`, `Downloading(percent?)`, `Started` (API 33, no progress), `Done`, `Failed(code)`.
- On `LANGUAGE_PACK_MISSING` the ViewModel checks fa-IR (`checkRecognitionSupport`, API 33+). Not installed: `Offer`. Queued: `Pending`. Otherwise no pack line.
- Records each error and engine in `SpeechDiagnostics` (in memory, last value only) for the Settings card.
- Never submits. Only the send action submits (`EntryAction.Submit`).
- UI: `ChatInputBar` (mic 56dp, status block under the row). `MicPermissionSheet`. `EntryScreen` (permission launcher, `ON_STOP` stops the mic, voice settings helper).

## Language pack (`LanguagePack.kt`, API 33+)
- `check`: short-lived recognizer, `checkRecognitionSupport`, destroyed in the callback.
- `download`: `triggerModelDownload`. API 34+ gives a listener (progress, success, error). API 33 gives `Started` only, and the user is told to try again later. The recognizer stays alive until Done or Failed, then it is destroyed.
- Below API 33: no download button. The `تنظیمات ورودی صدا` button opens `Settings.ACTION_VOICE_INPUT_SETTINGS`, shown only when it resolves (`<queries>` in the manifest).

## Settings diagnostics (`SpeechReportCard.kt`)
- Card «تشخیص گفتار» in the About section. Shows: API level, service available, on-device available, service package names, fa-IR state, last error (kind and code, engine).
- «کپی گزارش» copies plain text to the clipboard. «بررسی دوباره» re-probes.
- No device model, no account data. Digits are Persian.
- The last error is in memory. It resets when the process restarts.

## States
| State | Mic | Field | Status line |
|---|---|---|---|
| Idle | `ضبط صدا`, primaryContainer | editable | none |
| Listening | `توقف ضبط`, primary; tap stops | read-only, partial text live-fills | `در حال گوش دادن…` (polite) |
| Error | idle | editable, text kept | error text in error color, muted `(کد …)` tail (polite) |
| Language pack missing | idle | editable | error + `دانلود بسته‌ی فارسی` (or settings button below API 33) |
| No permission | tap opens rationale sheet | editable | none |
| Permanently denied | tap opens denied sheet | editable | none |
| Busy (draft/parse running) | disabled | read-only | none |

Error text (`SpeechErrorKind` to string, `feature/entry/src/main/res/values/strings.xml`):
- `NO_MATCH`: `صدا واضح نبود...`
- `NO_RECOGNIZER_INSTALLED`: service missing. Tells the user to install or enable the «Google» app.
- `LANGUAGE_PACK_MISSING`: pack missing. Offers the download.
- `BUSY`, `NETWORK`, `NO_PERMISSION`, `UNSUPPORTED`, `OTHER` (generic).

## Permission flow (RECORD_AUDIO)
1. Tap mic. Already granted: start listening.
2. Not granted: rationale sheet (`اجازه دسترسی` / `فعلاً نه`). The system dialog opens only from the sheet button.
3. Granted: start with the current input text as the base.
4. Denied, and the system still allows asking: sheet closes. Next tap shows the rationale again.
5. Denied, and the system does not allow asking again: denied sheet with `باز کردن تنظیمات`.
6. Permission checked live (`checkSelfPermission`).
Manifest: `RECORD_AUDIO`. Package visibility: `<queries>` for `android.speech.RecognitionService` and `android.settings.VOICE_INPUT_SETTINGS` (Android 11+).

## Input rules
- Partial and final text only fill the field. The user reads it and taps send.
- Field is read-only while listening, because partial results live-fill the same field. Stop the mic to edit.
- Stopping the mic (tap or `ON_STOP`) asks the recognizer to finish. Listening ends when its final result or error arrives.
- Numbers may come as digits or words. The normal pipeline (`NumberWordParser`) handles both.

## Known gaps / open
- The listening pulse ring (DESIGN 5.2, decorative) is not built.
- If the recognizer never sends a result or error after stop, listening stays on until the next event.
- The fallback fires only on the codes above. Other on-device failures do not retry on the system recognizer.
- The download on API 33 has no progress. If the recognizer is destroyed mid-download the platform may cancel it.
- `checkRecognitionSupport` and `triggerModelDownload` are called on the main thread. Lint NewApi is guarded by inline `SDK_INT` checks, not by annotations (no `androidx.annotation` in the catalog).
- No cloud implementation. Swap the binding in `DataModule` to add one (T09c).

## Manual device checklist (not testable in CI)
Persian recognition depends on the Google app and its offline fa pack, and may need internet. Run on a real phone with the CI APK (`docs/CI.md`).
- [ ] First mic tap shows the rationale sheet in Persian. `اجازه دسترسی` opens the system dialog. Allow it. Listening starts.
- [ ] Say `۴ قوطی نوشابه روی هم پانصد هزار تومان خریدم`. Partial text appears in the field while speaking. The final text stays in the field. Nothing is sent.
- [ ] Silence for a few seconds: `صدا واضح نبود` appears. Text input still works.
- [ ] Fa pack not installed (on-device): the error appears with `(کد ۱۳)` or `(کد ۱۲)` and the download button (API 33+). After download, retry works.
- [ ] Airplane mode with the default recognizer: network text appears. Typing still works.
- [ ] Deny the dialog once, tap mic: rationale again. Deny again: denied sheet. `باز کردن تنظیمات` opens app settings.
- [ ] Put the app in the background while listening: listening stops, no crash.
- [ ] No recognition service (or disabled): service-missing text and the voice settings button if the phone has that screen.
- [ ] Settings, About: «تشخیص گفتار» card shows the facts. `کپی گزارش` pastes the report. `بررسی دوباره` refreshes.
- [ ] Font scale 200%: status block wraps, nothing clipped, mic and buttons tappable.
- [ ] TalkBack: mic reads `ضبط صدا`; while listening `توقف ضبط`; status line announced; download button reads its label.
