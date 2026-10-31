# VOICE
Status: on-device with one fallback to the system recognizer (T09b). Cloud STT via the backend (T09c): code done, real-mic and real-provider accuracy unverified (see checklist).

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
- Cloud engine (T09c): see section "Cloud engine (T09c)" below.
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
- Cloud engine: no live provider test in CI. Accuracy of Persian transcription through the backend is unverified until the manual checklist (T09c).

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

## Cloud engine (T09c)
- Wiring: `DataModule.provideSpeechSource` -> `createVoiceSpeech` (core:data `speech/VoiceSpeech.kt`) = `RoutingSpeechSource(local = AndroidSpeechSource, cloud = CloudSpeechSource, prefs = VoicePreferences)`.
- Setting `VoiceEngine` (SettingsRepository key `voice_engine`, default AUTO), in Settings under «ورودی صدا»:
  - `AUTO` «خودکار»: on-device/system first. Fallback to cloud ONCE per session, only when the local attempt fails with NO_RECOGNIZER_INSTALLED, LANGUAGE_PACK_MISSING or NETWORK, and a backend is saved and consent is given. The local error is not shown; `EngineChanged(CLOUD)` is.
  - `DEVICE_ONLY` «فقط روی دستگاه»: never uploads.
  - `CLOUD_ONLY` «فقط ابری»: always records and uploads.
- `CloudSpeechSource`: `MediaRecorder` MPEG-4/AAC, 16 kHz mono, 64 kbps, to `cacheDir/stt-<uuid>.m4a`. Auto-stop at 30 s. `stop()` ends recording and uploads. Events: `Processing` (not text: the field is left alone), then `Final(text)` or `Error(kind)`. Temp file deleted on every path (success, failure, no-audio, release).
- Upload (`TranscriptionHttp`): `POST {baseUrl}/v1/audio/transcriptions`, multipart `file` (audio/mp4, `audio.m4a`), `language=fa`, `response_format=json`, `Authorization: Bearer <token>`. No model field (the server picks it). Status mapping: 401/403 CLOUD_UNAUTHORIZED, 413 CLOUD_TOO_LARGE, 429 CLOUD_RATE_LIMITED, 501 CLOUD_NOT_CONFIGURED, other 4xx OTHER, 5xx and network CLOUD_UNAVAILABLE.
- Consent: the first time cloud may be used (backend saved, engine not DEVICE_ONLY, consent not yet given), tapping the mic shows «صدای شما برای تبدیل به متن به سرور ارسال می‌شود». «موافقم» stores consent and starts. «فعلاً نه» starts nothing. Consent is stored once (`cloud_stt_consent`).
- UI: while listening the status line names the engine («ابری» / «دستگاه»). During upload it shows «در حال تبدیل صدا به متن…» and the mic is off. Text only fills the field. Nothing is submitted.
- Stop on background (ON_STOP) is a normal Stop: a recording in progress is uploaded. Documented choice.
- Deviation from the T09c text: the upload status is a `SpeechEvent.Processing` event, not `Partial("در حال تبدیل…")`, so the status never lands in the input field.

### Privacy (what leaves the phone)
- Text and voice follow the same rule: nothing is sent unless the backend is configured.
- Audio is sent only by the cloud engine: in CLOUD_ONLY always, in AUTO only after the on-device engine fails and consent is given. Never in DEVICE_ONLY.
- The app does not store audio. The temp file is deleted after the upload attempt.
- What the backend and its speech provider do with audio depends on the server you configured (`STT_BASE_URL`, see docs/BACKEND.md).
- On-device recognition may use the internet on some phones (the phone's speech service decides). This is unchanged.

### Tests
- core:data: `CloudSpeechSourceTest` (fake recorder + transcriber: permission, config, busy, upload mapping, temp-file deletion, auto-stop), `RoutingSpeechSourceTest` (mode routing, one AUTO fallback, consent/config gates, inactive events dropped), `TranscriptionHttpTest` (MockWebServer: bearer, multipart fields, no model field, status mapping, unreachable), `VoiceEngineTest` (persisted setting and consent).
- feature:entry: `VoiceConsentTest` (consent gate, decline, processing, engine label, typed cloud errors), `VoiceViewModelTest` and `EntryViewModelTest` (constructor updated with `FakeVoicePreferences`), `EntryScreenTest` (cloud/device label, consent dialog confirm and cancel).
- Not tested: a real microphone, a real `MediaRecorder`, a real provider.

### Manual checklist for the user (cloud, T09c)
Needs a running backend with STT configured, and a phone that can reach it.
- [ ] Backend: set `STT_BASE_URL` (and `STT_API_KEY` if the provider needs one), `STT_MODEL`, run `npm start`. `curl` the transcription route with a small Persian clip; expect `{"text": ...}`.
- [ ] App: Settings > «ورودی صدا» > «فقط ابری». In Settings, the backend URL and token are set (same as the parser).
- [ ] Tap the mic. The consent dialog shows «صدای شما برای تبدیل به متن به سرور ارسال می‌شود». Tap «موافقم». The label says «ابری» while listening.
- [ ] Say `۴ قوطی نوشابه روی هم پانصد هزار تومان خریدم`. The status shows «در حال تبدیل صدا به متن…», then the text fills the field. Nothing is sent. Send it and check the draft shows `۵۰۰٬۰۰۰ تومان`.
- [ ] Stop the backend. Tap the mic. The message «سرور تبدیل صدا در دسترس نیست…» appears. Typing still works.
- [ ] Set the token to a wrong value. The unauthorized message appears, and the token message names the settings.
- [ ] Set «فقط روی دستگاه». Tap the mic. No upload happens (check the backend log has no `stt` line).
- [ ] Set «خودکار» and remove the backend URL. Tap the mic: no consent dialog, on-device behaviour unchanged.
- [ ] Background the app while recording: the recording ends and uploads (documented choice). No crash.

