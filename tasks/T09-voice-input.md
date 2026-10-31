# T09 — voice-input
Status: todo
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
