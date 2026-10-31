# T07 — nl-pipeline
Status: in_progress
Depends: T03, T04, T05, T06
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

## Added by main agent (binding)
- Also read `handouts/T04-room-persistence.md` (LedgerRepository, PartyRepository: `resolve()` is BLOCKING -> call off main thread), `handouts/T01d-ci-green.md`, `handouts/T01e-auto-release.md` (every green push creates a release: avoid noisy WIP pushes; max 6 pushes).
- Placement: pure logic (`NlPipeline`, `Draft`, `PipelineResult`, cross-check, `NlParser` interface, intent DTOs incl. backend extras `amount_text`/`amount_value`/`amount_unit`/`amount_rial`) goes in `core/domain` (kotlin-jvm; add `implementation(project(":core:persian"))`; no android.*, no Float/Double). Verify locally in a scratch Gradle project under /tmp (copy the pattern from /tmp/domain-verify or /tmp/persian-verify if present), report command + counts. HTTP client `NlParserHttp` goes in `core/data` using `java.net.HttpURLConnection` or OkHttp (verify on Maven Central) with `MockWebServer` tests run by CI (`./gradlew test`); base URL + bearer token injected via constructor (no hardcoded values; settings UI is T12). Never log request text or token.
- Result model (document in handout): `sealed interface PipelineResult { Draft(entry: JournalEntry, intent, needsConfirm: Boolean, reasons: List<ConfirmReason>) ; NeedsClarification(question: String) ; Failure(error: PipelineError) }`; `PipelineError`: Offline, Unauthorized(401), RateLimited(429, retryAfter?), ServerError(5xx), BadResponse(malformed JSON / schema violation / DomainException), EmptyInput, AmountMissing.
- Cross-check rules (from T06 handout): (1) parse input locally with `NumberWordParser.parseAmount`; (2) if backend `amount_rial` != local `rial` -> `needsConfirm` with reason AMOUNT_MISMATCH carrying both candidates; (3) if local parser returns null/ambiguous but backend has an amount -> needsConfirm AMOUNT_UNVERIFIED; (4) if `amount_text` is not a substring of normalized input -> needsConfirm; (5) LLM `confidence` < 70 -> needsConfirm LOW_CONFIDENCE. Never post silently on mismatch. Local parse is source of truth when user picks "use detected amount" (provide `Draft.withAmount(rial)` re-posting through PostingEngine).
- `intent.date`: the pipeline sends `today` (injected `Clock`) per backend contract; relative-date resolution is the backend's job; pipeline validates date within the open fiscal year else `Failure(BadResponse)` -> or a typed `DateOutsideFiscalYear` (add to PipelineError).
- Party resolution happens at post time through the injected `PartyAccountResolver` (T04); pipeline itself never touches Room; a `Draft` is NOT committed (T08 commits via LedgerRepository after user confirms).
- Test list minimum: soda example end-to-end with fakes (cash purchase, 500,000 toman -> 5,000,000 rial, Dr 5100 / Cr 1100, balanced), credit purchase from named person, mismatch, unverified, clarification, each error type, empty input, Persian-digit input, injection-looking text still flows only through schema validation (no extra behaviour).
