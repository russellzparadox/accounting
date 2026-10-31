# T09 — voice-input
Status: in_progress
Depends: T08
Read first: CLAUDE.md, handouts/T08-entry-ui.md
Skills to invoke: design:ux-copy, persian-writing:persian-writing

## Goal
- `SpeechSource` interface. On-device fa-IR `SpeechRecognizer` impl. Mic UI. Permission flow. Partial results. Error states. Cloud impl stub only.

## Deliverables (files)
- `core/data/.../speech/SpeechSource.kt` (interface)
- `core/data/.../speech/AndroidSpeechSource.kt` (on-device)
- `core/data/.../speech/CloudSpeechSourceStub.kt`
- Mic button and permission UI in `feature/entry`
- `docs/VOICE.md`
- Tests with fake `SpeechSource`

## Acceptance criteria
- [ ] `SpeechSource` exposes start, stop, and a flow of partial, final, and error events.
- [ ] On-device impl uses `fa-IR` and partial results on.
- [ ] `RECORD_AUDIO` flow: rationale, denied state, link to app settings.
- [ ] Error states mapped: no match, recognizer unavailable, busy, no permission.
- [ ] Cloud stub returns an explicit unsupported result. Test.
- [ ] Mic button >= 48dp, shows idle and listening states.
- [ ] `docs/VOICE.md` written.
- [ ] ViewModel tests with fake `SpeechSource` pass.

## Out of scope
- Cloud STT implementation, wake word, audio recording files.

## Verify (commands)
- `./gradlew test assembleDebug`

## Added by main agent (binding)
- Read also `handouts/T08a-entry-viewmodel.md`, `handouts/T01d-ci-green.md`, `handouts/T01e-auto-release.md`, `docs/CI.md`. T08 reserved a disabled mic slot with a "به‌زودی" hint in the input bar: enable it. Replace the placeholder, don't add a second button.
- Recognizer choice: on API 31+ prefer `SpeechRecognizer.createOnDeviceSpeechRecognizer` when `isOnDeviceRecognitionAvailable`; otherwise default `createSpeechRecognizer` with `EXTRA_PREFER_OFFLINE=true`. Always `EXTRA_LANGUAGE=fa-IR`, `EXTRA_PARTIAL_RESULTS=true`, `EXTRA_MAX_RESULTS=3`. Check `SpeechRecognizer.isRecognitionAvailable`; add `<queries>` for the recognition service intent in the manifest (Android 11+ package visibility). Create/destroy the recognizer on the main thread, release in `onCleared`/lifecycle stop. One session at a time (BUSY error).
- Voice must NEVER auto-submit. Partial results live-fill the input text field (user can edit); a final result stays in the field. Submit remains the existing send action. Optional: a setting is not needed now.
- Persian speech numbers: recognizers return either digits or words ("پانصد هزار تومان"); the existing normalizer + `NumberWordParser` already handle both: add a few test sentences in the voice-to-pipeline test path (fake SpeechSource emitting `۴ قوطی نوشابه ... پانصد هزار تومان خریدم`).
- Permission UI: `RECORD_AUDIO` via `rememberLauncherForActivityResult(RequestPermission)`; rationale bottom sheet text in Persian (design:ux-copy), denied-permanently state with button to app settings; text input continues to work in every voice state.
- Real microphone behaviour cannot be tested in CI or here: tests use a fake `SpeechSource`; document in the handout a short manual device checklist for the user (Persian recognition availability depends on Google app / offline fa pack, and may need internet).
- Verification is CI-only: push ONE commit per attempt, wait for THAT run to finish before pushing again (poll the public API `https://api.github.com/repos/russellzparadox/accounting/actions/runs/<id>` with `until` in Bash run_in_background); read the 'Failure summary' step with mcp__github__get_job_logs tail_lines <= 130. Max 6 pushes. Do NOT end your turn until the final run on your last push completed and the handout is written. Each green push releases an APK the user installs.
