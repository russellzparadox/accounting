# Handout T09c — cloud-stt
Status: partial (code done and CI green; real mic + real provider unverified; see Open issues)

## Summary
- Backend: `POST /v1/audio/transcriptions` (OpenAI-compatible multipart), STT upstream client, `STT_*` env, tests incl. official `openai` SDK. No new dependency.
- Android: `CloudSpeechSource` (MediaRecorder, 30 s cap, upload), `RoutingSpeechSource` (AUTO / DEVICE_ONLY / CLOUD_ONLY, one AUTO fallback), Settings «ورودی صدا», one-time consent dialog, engine label, privacy copy, tests.
- CI: see "Final runs" below.

## Files (new or changed)
- Backend: `backend/src/stt.ts` (fields, multipart via `Request.formData()`, `OpenAiSttClient`), `backend/src/server.ts` (route, 501, errors, logs), `backend/src/config.ts` (`STT_BASE_URL`, `STT_API_KEY`, `STT_MODEL`, `STT_TIMEOUT_MS`), `backend/src/index.ts` (client wiring), `backend/test/stt.test.ts`, `backend/test/stt_sdk.test.ts`, `backend/test/config.test.ts`, `backend/.env.example`, `docs/BACKEND.md`.
- Android core:data `speech/`: `CloudSpeechSource.kt`, `RoutingSpeechSource.kt`, `TranscriptionHttp.kt`, `AudioRecorder.kt`, `VoicePreferences.kt` (`VoiceEngine`, `VoicePreferences`, `SettingsVoicePreferences`), `VoiceSpeech.kt` (`createVoiceSpeech`), `SpeechSource.kt` (new kinds, `SpeechEngine.CLOUD`, `SpeechStartResult.NOT_CONFIGURED`, `SpeechEvent.Processing`). Removed `CloudSpeechSourceStub.kt`.
- core:data `config/`: `SettingsRepository.kt` (`voiceEngine`, `setVoiceEngine`, `cloudSttConsented`, `acceptCloudStt`), `ConfigStore.kt` (`current`).
- app `di/DataModule.kt`: `provideVoicePreferences`, `provideSpeechSource` = `createVoiceSpeech`.
- feature:entry: `VoiceViewModel.kt` (consent gate, `processing`, `engine`), `VoiceInput.kt` (`messageRes` cases, `CloudConsentDialog`), `ChatInputBar.kt` (engine label, processing), `EntryScreen.kt` (dialog wiring), `res/values/strings.xml`.
- feature:settings: `SettingsScreen.kt` (`VoiceSection`), `SettingsViewModel.kt` (`SetVoiceEngine`), `SpeechReportCard.kt` (labels), `res/values/strings.xml` (voice strings, privacy array).
- Tests: core:data `CloudSpeechSourceTest`, `RoutingSpeechSourceTest`, `TranscriptionHttpTest`, `VoiceEngineTest`, `VoiceFakes`; feature:entry `VoiceConsentTest`, `FakeVoicePreferences`, `EntryScreenTest` (4 new), `VoiceViewModelTest`/`EntryViewModelTest` (constructor only); `SpeechSourceTest` (stub test removed).
- `docs/VOICE.md` (cloud section, privacy, checklist).

## Public interfaces / contracts
- `POST /v1/audio/transcriptions` (Bearer). Fields: `file` (req), `language` (default `fa`), `prompt` (default Persian vocabulary; empty = none; <= 1000 chars), `response_format` json|text|verbose_json; `model`, `temperature` accepted, ignored (`STT_MODEL` used). Returns `{"text"}` (text/plain for `text`).
- Errors (OpenAI shape): 400 `invalid_multipart|missing_file|empty_file|invalid_language|invalid_response_format|prompt_too_long|invalid_audio`; 401; 405; 413 `file_too_large|request_too_large`; 415 `unsupported_file_type`; 429 `rate_limit_exceeded`; 501 `not_configured`; 502 `stt_unavailable`.
- Android: `interface SpeechSource` unchanged. `CloudSpeechSource(scope, cacheDir, config, recorder, transcriber, hasPermission)`. `RoutingSpeechSource(local, cloud, prefs, scope)`. `VoicePreferences { engine, cloudConfigured, cloudConsented, acceptCloudConsent() }`. `VoiceEngine { AUTO, DEVICE_ONLY, CLOUD_ONLY }`.
- `VoiceViewModel(speech, diagnostics, languagePack, prefs)`. Actions `ConfirmCloudConsent`, `DismissCloudConsent`. State `consentRequired`, `processing`, `engine`.
- Settings keys: `voice_engine` (default AUTO), `cloud_stt_consent`.

## Final runs (branch claude/dreamy-mccarthy-mczm8c)
- Backend: 41214340198 on 8aa5413, success.
- Android (code): 41215292301 on ba0637c, success (build: lint, test, assembleDebug; publish success).
- Android (docs/status, this handout): 41215875462 on 435b087, success.
- Pushes used on this task: 8aa5413, ba0637c, 435b087, plus the handout-fix push below (max 6 for the Android part).

## Audit (items 1-8 of tasks/T09c-cloud-stt.md)
Legend: MET / PARTIAL / NOT MET. Evidence = file:line.

| # | Item | Result | Evidence |
|---|---|---|---|
| 1 | `POST /v1/audio/transcriptions`, multipart fields, Bearer + rate limit, 10 MB cap, type allowlist, OpenAI errors, `{"text"}` | MET (duration sanity PARTIAL: size cap only, no decoding; app caps at 30 s) | server.ts:157-190 (handler: auth, rate limit, body cap at 176, parse at 185); stt.ts:8-10 (caps), 95 (file cap), 19-48 (type allowlist), 90-150 (fields); server.ts:239 (route) |
| 2 | Upstream client, `STT_*` env, `fa` + Persian prompt default, 501 `not_configured`, no audio/transcript logs (DEBUG_LOG_TEXT only), no key leak, capped input | MET | stt.ts:171 (`OpenAiSttClient`), config.ts:18-25 and 66-69, server.ts:165 (501), server.ts:191 (`stt` log: no audio, no filename; `text` dropped unless logText), stt.test.ts "no-log guarantee" |
| 3 | Tests: multipart, auth/429/size/type, stub upstream success, 501, no-log, official `openai` SDK | MET | stt.test.ts + stt_sdk.test.ts + config.test.ts = 40 tests (suite 211 total, local `npm test` green), stt_sdk.test.ts (`audio.transcriptions.create`, text format, 401) |
| 4 | `CloudSpeechSource`: MediaRecorder AAC 16 kHz mono 64 kbps, 30 s auto-stop, upload with bearer, typed errors, temp file deleted, mic permission reused, never auto-submit | PARTIAL (deviation: status is `SpeechEvent.Processing`, not `Partial("در حال تبدیل…")`, so the status never lands in the field) | CloudSpeechSource.kt:41 (start), 58 (stop), 85 (release), 64/74 (delete), 70 (Processing), 101 (MAX_RECORD_MS); AudioRecorder.kt:31-48; TranscriptionHttp.kt:91 (status decode table); VoiceInput.kt:63-76 (messageRes cloud kinds, NOT_CONFIGURED text «سرور برای تبدیل گفتار تنظیم نشده»); VoiceViewModel.kt:145-160 (start result mapping) |
| 5 | Engine setting «خودکار» / «فقط روی دستگاه» / «فقط ابری» in Settings, default AUTO, labels, first-use consent text | MET | SettingsScreen.kt:156, 241 (VoiceSection); SettingsRepository.kt:24-30, 53-60; RoutingSpeechSource.kt:37-45 (mode), 75-90 (AUTO fallback); ChatInputBar.kt:300 (`engineLabel`); VoiceViewModel.kt:118-135 (consent gate); strings.xml `voice_consent_body` = «صدای شما برای تبدیل به متن به سرور ارسال می‌شود.» |
| 6 | Privacy copy (onboarding + settings) updated for audio; other points accurate | MET | feature/settings res strings.xml `privacy_points` (8 items; items 2, 3, 7 changed). Onboarding and settings both use `PrivacyPoints` (OnboardingScreen.kt:56-57). |
| 7 | Tests: VM / routing with fakes (fallback order, modes, NotConfigured); Robolectric upload shape via MockWebServer (multipart, bearer, file deletion); UI test for label + consent | PARTIAL (file deletion checked with fake recorder/transcriber in CloudSpeechSourceTest, not inside the MockWebServer test) | RoutingSpeechSourceTest.kt (14 tests), VoiceConsentTest.kt (10), TranscriptionHttpTest.kt (6, MockWebServer: bearer, multipart, mapping), CloudSpeechSourceTest.kt (9 tests, deletion), EntryScreenTest.kt `listeningLabel*`, `cloudConsent*`; docs/VOICE.md tests section |
| 8 | Honest limits + manual checklist for the user | MET | docs/VOICE.md «Manual checklist for the user (cloud, T09c)»; this handout (Open issues) |

Totals: 5 MET, 3 PARTIAL (4, 7 and the duration sub-point of 1), 0 NOT MET. The three PARTIALs are wording or scope, not missing behaviour, but they are listed as they are.

## Decisions made (+why)
- `Processing` event instead of `Partial("در حال تبدیل…")`: a Partial fills the input field, so the status text would have been typed into the user's draft.
- Consent is asked once, before any recording in AUTO and CLOUD_ONLY (backend saved, engine not DEVICE_ONLY). Declining starts nothing. Why: the user must know before audio leaves the phone; the fallback runs inside a session, so the check cannot wait for it.
- AUTO fallback reports the cloud attempt's result and hides the local error. Why: the local error is not actionable once the cloud answers.
- Stop on ON_STOP uploads a running recording (`Stop` semantics kept). Why: one code path; documented in VOICE.md.
- Cloud `model` is never sent; server picks `STT_MODEL`. Why: the client cannot pick a costlier upstream model.
- Old stub test removed (its class is gone). Replaced by CloudSpeechSourceTest.
- `TranscriptionHttp` uses HttpURLConnection like `NlParserHttp`, no new dependency.

## How verified (commands + result)
- Backend local: `cd backend && npm ci && npm test && npm run build` (Node 22.23.4): 211 tests pass (11 files), `tsc` OK. Node 20 (CI) verified by backend run 41214340198.
- Android: no local Gradle (Google Maven blocked). CI only.
  - Android run 41215292301 on ba0637c: success. Build job: `Lint, test, assemble debug` = success. Publish success.
  - Backend run 41214340198 on 8aa5413: success.
- Not verified: device behaviour, real `MediaRecorder` output, real provider accuracy.

## Open issues / risks
1. Real mic + real STT provider accuracy for Persian: unverified. Needs the manual checklist (docs/VOICE.md).
2. Duration sanity: backend checks size only (10 MB), not duration. App caps recording at 30 s.
3. `Processing` deviation from the spec text (see Decisions).
4. Commit attribution: backend commit 8aa5413 carries `Co-Authored-By: Claude Haiku 5.5` (the attribution reminder in that session named Haiku). Android commits use the trailer the T09c instructions specify (Sonnet 5.5). Not rewritten (no force-push). Pick one for the final history.
5. Concurrent agent: commit db62934 (WIP checkpoint) was made by an agent in the same checkout during this task; it contains the same backend sources as 8aa5413. Treated as the same work, not as a second implementation.
6. Skills: `persian-writing` and `design:ux-copy` were not invoked in this run. Persian strings were written by hand and checked for XML validity only. Copy review is open.
7. ON_STOP uploads an in-progress recording (see Decisions).
8. Lint/Compose behaviour of the new dialog and label is covered by Robolectric UI tests only; not visually reviewed.

## Manual checklist for the user
See docs/VOICE.md «Manual checklist for the user (cloud, T09c)» (9 items: backend env and curl, consent, label, transcript fills field and nothing is sent, backend down message, wrong token message, device-only makes no upload, AUTO without backend, background during recording).

## Next task must know
- `SpeechSource` is unchanged except new enum members. Any new `when` over `SpeechErrorKind` / `SpeechEngine` / `SpeechStartResult` / `SpeechEvent` must cover the new members (VoiceInput.kt messageRes, SpeechReportCard labels, VoiceViewModel handle).
- Voice still never submits. Keep `Submit` user-triggered.
- Backend STT is OpenAI-compatible; live provider unverified. T13 should run one real Persian clip.
