# T07 — nl-pipeline
Status: todo
Depends: T03, T05, T06
Read first: CLAUDE.md, handouts/T03-domain-core.md, handouts/T05-persian-utils.md, handouts/T06-backend-llm-proxy.md
Skills to invoke: none

## Goal
- App-side NL pipeline: normalize -> NlParser client -> intent -> PostingEngine draft. Local amount cross-check. Clarification flow. Offline and error states. Fakes for tests.

## Deliverables (files)
- `core/data/.../nlp/NlParser.kt` (interface), `NlParserHttp.kt` (client)
- `core/domain/.../pipeline/NlPipeline.kt` (pure: normalize, cross-check, draft)
- Fakes: `core/data/src/test/.../FakeNlParser.kt`
- Tests for pipeline and client

## Acceptance criteria
- [ ] Flow: normalize (T05) -> `NlParser.parse` -> intent -> PostingEngine (T03) -> `Draft` with balanced lines.
- [ ] If LLM amount != locally parsed amount, Draft flagged `needsConfirm`. Test.
- [ ] `needs_clarification` response -> clarification question in result state. Test.
- [ ] Network error, 401, 429, 5xx each map to a typed error. Test each.
- [ ] Fakes usable in tests. Unit tests make no network calls.
- [ ] No Room import. Pipeline never writes to DB.

## Out of scope
- UI, voice, DB writes, backend changes.

## Verify (commands)
- `./gradlew test`
