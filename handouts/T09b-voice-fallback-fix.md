# Handout T09b — voice-fallback-fix
Status: done

## Summary
- Fallback chain, typed errors with Persian text and «(کد N)» tail, fa-IR pack check/download, Settings «تشخیص گفتار» card.
- Fix rounds: 201d4f3 (code, CI RED: compile), 3fd116c (compile fix, RED), 08fceec (test ctor fix, GREEN).
- Final green CI: android run 41213284854 (head 08fceec), conclusion success. build job success (lint, test, assembleDebug, APK upload), publish job success (build-146 release, latest tag moved).
- Audit: 6 items, 6 MET, 0 PARTIAL, 0 NOT MET.

## Fix round 1 (this session)
- Root cause: `EntryViewModelTest.kt:356` built `VoiceViewModel(speech)`; ctor now takes `(speech, diagnostics, languagePack)`. Errors: `No value passed for parameter 'diagnostics'` / `'languagePack'`.
- Fix: `VoiceViewModel(speech, SpeechDiagnostics(), FakeLanguagePack())` + import. No test weakened or skipped.
- Reviewed by hand (CI is only compiler): all `android.speech` imports top-level (`RecognitionSupport`, `RecognitionSupportCallback`, `ModelDownloadListener`); API 31/33/34 calls inside `Build.VERSION.SDK_INT` guards (`RecognizerPort.kt:90,93,155,176,186`); Robolectric `@Config(sdk=[34])` on chain, VM and settings tests.
- Pushes: earlier 201d4f3 (RED), 3fd116c (RED); this session 08fceec (GREEN), then one docs push (status/handout). 3 of 5 code+doc pushes used by T09b overall before docs; max 5 not exceeded.

## Fallback chain (`AndroidSpeechSource.kt`)
```
start()
 -> permission? no -> NO_PERMISSION (no recognizer)
 -> service? no -> UNAVAILABLE -> NO_RECOGNIZER_INSTALLED (no recognizer)
 -> on-device available (API 31+)? yes -> attempt 1 ON_DEVICE
                                     no  -> attempt 1 SYSTEM
attempt 1 ON_DEVICE error in {5,11,12,13} and attempts<2 -> destroy, attempt 2 SYSTEM (no PREFER_OFFLINE)
attempt 2 or any other error -> Error(kind, code), listening=false
results blank -> NO_MATCH
stale callbacks (old attemptId) ignored
every attempt emits EngineChanged(engine, afterErrorCode)
```
- Recognizer destroyed before each new attempt (`:60`), and in `release()` (`:50`).
- Main thread only (doc `:12-19`).

## Error kinds and Persian text (`values/strings.xml:60-76`, `VoiceInput.kt:58-70`)
| Kind | Trigger | Text (fa) |
|---|---|---|
| NO_RECOGNIZER_INSTALLED | start UNAVAILABLE (no service) | سرویس تشخیص گفتار روی این گوشی پیدا نشد. برنامه‌ی «Google» را نصب یا فعال کنید. ... |
| LANGUAGE_PACK_MISSING | codes 12, 13 | بسته‌ی فارسی تشخیص گفتار روی گوشی نصب نیست. دانلود کنید یا متن را تایپ کنید. |
| NETWORK | codes 1, 2, 4, 11 | اتصال برقرار نشد. اینترنت را بررسی کنید یا متن را تایپ کنید. |
| NO_MATCH | codes 6, 7, empty result | صدا واضح نبود. دوباره بگویید. |
| BUSY | codes 8, 10 | تشخیص صدا هنوز مشغول است. ... |
| NO_PERMISSION | start refused / code 9 | دسترسی میکروفون لازم است. ... |
| UNSUPPORTED | Cloud stub | ضبط صدا در این نسخه فعال نیست. ... |
| OTHER | everything else | صدا پردازش نشد. ... |
- Code tail «(کد ۱۳)» shown muted: `ChatInputBar.kt:186-193`, `voice_error_code`.

## Language pack (API 33+)
- `LanguagePack.kt` `LanguagePackController`: check (`checkRecognitionSupport`), download (`triggerModelDownload`), progress on API 34 via `ModelDownloadListener`; API 33 = Started, no progress.
- UI: `ChatInputBar.kt:219-240`. Button «دانلود بسته‌ی فارسی» for `PackUi.Offer`; status lines for pending/downloading/done/failed.
- Below API 33: `PackStatus.UNSUPPORTED_API` -> no download button; settings link «تنظیمات ورودی صدا» (`ACTION_VOICE_INPUT_SETTINGS`) shown only when `resolveActivity` finds a handler (`VoiceInput.kt:46-50`, `ChatInputBar.kt:210-211`).

## Settings diagnostics card
- `feature/settings/.../SpeechReportCard.kt`, placed in `SettingsScreen.kt:173` right after the About section.
- Shows: Android API level, recognition service available, on-device available, installed recognition service packages, fa-IR pack state, last error kind + code + engine.
- Buttons: `بررسی دوباره` (refresh), `کپی گزارش` (plain text, no PII, `copySpeechReport` `:129`).

## Audit (items 1-6)
| # | Item | Status | Evidence |
|---|---|---|---|
| 1 | Fallback chain, one retry, never loop, release between attempts, EngineChanged | MET | `AndroidSpeechSource.kt:15-16,50-64,79-84,100,110`. Tests `AndroidSpeechSourceChainTest` (`onDeviceLanguageErrorFallsBackToSystemOnceAndSucceeds`, `everyFallbackCodeTriggersExactlyOneRetry`, `fallbackFailingWithLanguageErrorGivesTypedErrorAndNeverLoops`, `releaseDestroysEveryRecognizerCreated`) |
| 2 | Distinct kinds, Persian messages, «(کد N)» tail | MET | `SpeechSource.kt:9-17`, `strings.xml:60-76`, `VoiceInput.kt:58-70`, `ChatInputBar.kt:186-193`. Tests `VoiceViewModelTest.startFailuresAreMappedToErrorsWithoutListening`, `EntryScreenTest.voiceErrorShowsItsPlatformCodeAsATail` |
| 3 | fa-IR check/download, API 33/34 split, settings link < 33 | MET | `LanguagePack.kt`, `RecognizerPort.kt:149-190`, `ChatInputBar.kt:219-240`, `VoiceInput.kt:46-50`. Tests `VoiceViewModelTest.downloadShowsProgressThenDone`, `EntryScreenTest.languagePackOfferShowsDownloadButtonAndTapDispatchesDownload` |
| 4 | Settings diagnostics card + copy | MET | `SpeechReportCard.kt:37-153`, `SettingsScreen.kt:173`. Tests `SpeechReportCardTest` (3 tests) |
| 5 | Tests: fake factory chain, max 2 attempts, release each time, VM/UI tests, docs/VOICE.md | MET | `AndroidSpeechSourceChainTest.kt` (fake factory `:248+`), `VoiceViewModelTest.kt` (18 tests), `EntryScreenTest.kt:107-168`, `docs/VOICE.md` rewritten (sections Fallback chain, Language pack, Settings diagnostics, Known gaps, Manual device checklist) |
| 6 | Manual retest checklist for the user, no claim of real-mic verification | MET | `docs/VOICE.md:99-111` (checklist) and below |

- Not claimed: real-device behaviour. CI proves compile + JVM/Robolectric tests only.
- Test counts per class not extracted from the CI log (test step passed; only the Gradle status was read).

## Open issues / risks
- Real-mic behaviour unverified (no device in CI). User must run the manual checklist.
- Cloud STT not implemented (T09c). Stub only.
- Listening may stay on if the recognizer never sends a result or error after stop (`docs/VOICE.md` Known gaps).
- API 33 download has no progress.
- Node 20 deprecation warnings in workflow actions (not in scope).

## Manual retest for the user (phone)
1. Install the newest build from the Releases page (`build-146` or newer). Settings > Apps > accounting shows `0.1.146` or higher.
2. Tap the mic. Grant the microphone permission. Say `۴ قوطی نوشابه روی هم پانصد هزار تومان خریدم`.
3. Expected: text fills the input field and nothing is sent or committed.
4. If an error shows, note the Persian message and the «(کد N)» tail.
5. If «دانلود بسته‌ی فارسی» is offered, tap it. Wait, then retry the mic.
6. Open Settings > «تشخیص گفتار», tap «کپی گزارش», and send the copied text to the developer if it still fails.
