# Handout T07 — nl-pipeline
Status: done

## Summary
- Pipeline: normalize -> `NlParser.parse(text)` -> cross-check vs `NumberWordParser` -> `PostingEngine.post` -> `PipelineResult`. Pure in core:domain. Client in core:data (HttpURLConnection, no new runtime deps).
- CI green: run https://github.com/russellzparadox/accounting/actions/runs/41185962987 on code commit a4fa7dc (`success`).
- Local (scratch, /tmp/nl-verify): domain 56/56 (26 existing + 30 new), persian 105/105, NlParserHttp 12/12 (MockWebServer, pure-JVM copy of core:data nlp).

## Files
- core/domain `pipeline/`: `NlParser.kt` (NlParser, ParserResult, ParsedIntent, PipelineError), `NlWire.kt`, `InputNormalizer.kt`, `NlPipeline.kt` (NlPipeline, PipelineResult, ConfirmReason).
- core/domain: `build.gradle.kts` + `implementation(project(":core:persian"))`; test-only `kotlinx-coroutines-core`.
- core/domain test `pipeline/`: `FakeNlParser.kt`, `InputNormalizerTest`, `NlWireTest`, `NlPipelineTest`.
- core/data `nlp/NlParserHttp.kt`; test `nlp/NlParserHttpTest.kt` (MockWebServer).
- `gradle/libs.versions.toml`: okhttp 4.12.0, `okhttp-mockwebserver` (test only).

## Public API (core:domain, package `com.example.accounting.core.domain.pipeline`)
```kotlin
interface NlParser { suspend fun parse(text: String): ParserResult }
sealed interface ParserResult {
    data class Intent(val parsed: ParsedIntent) : ParserResult
    data class Clarification(val question: String) : ParserResult
    data class Error(val error: PipelineError) : ParserResult
}
data class ParsedIntent(val intent: TransactionIntent, val amountText: String?, val amountValue: Long?, val amountUnit: AmountUnit?)
sealed interface PipelineError {
    data object Offline; data object Unauthorized
    data class RateLimited(val retryAfterSeconds: Long?)
    data class ServerError(val status: Int)
    data class BadResponse(val reason: String)
    data object EmptyInput; data object AmountMissing
    data class DateOutsideFiscalYear(val date: LocalDate)
}
sealed interface ConfirmReason {
    data class AmountMismatch(val llmRial: Long, val localRial: Long)
    data class AmountUnverified(val llmRial: Long, val localCandidatesRial: List<Long>)
    data class AmountTextUnverified(val amountText: String?)
    data class LowConfidence(val confidence: Int)
}
sealed interface PipelineResult {
    class Draft internal constructor(val entry: JournalEntry, val intent: TransactionIntent,
        val needsConfirm: Boolean, val reasons: List<ConfirmReason>, engine: PostingEngine) : PipelineResult {
        fun withAmount(rial: Long): Draft   // re-posts via PostingEngine; drops amount reasons, keeps LowConfidence
    }
    data class NeedsClarification(val question: String) : PipelineResult
    data class Failure(val error: PipelineError) : PipelineResult
}
class NlPipeline(parser: NlParser, engine: PostingEngine, fiscalYear: FiscalYear, minConfidence: Int = 70) {
    suspend fun process(rawText: String): PipelineResult
}
object InputNormalizer { fun normalize(raw: String): String }   // toLatin, ي/ك->ی/ک, collapse ws, trim; keeps ZWNJ
object NlWire {
    fun requestBody(text: String): String                      // {"text": ...} only
    fun decode(status: Int, body: String, retryAfter: String? = null): ParserResult
}
```
core:data (package `com.example.accounting.core.data.nlp`):
```kotlin
class NlParserHttp(baseUrl: String, token: String, connectTimeoutMs: Int = 10_000, readTimeoutMs: Int = 30_000) : NlParser
// POST {baseUrl}/v1/parse, Authorization: Bearer, Dispatchers.IO. IOException -> Offline.
```

## Result rules (process order)
1. Normalize; blank -> `Failure(EmptyInput)` (parser not called).
2. Parser `Error` -> `Failure(error)`. `Clarification` -> `NeedsClarification(question)`.
3. `intent.date` outside FY -> `Failure(DateOutsideFiscalYear)`.
4. Local `NumberWordParser.parseAmount(normalized)`; if null AND no amount_text -> `Failure(AmountMissing)`.
5. Reasons: local null -> AmountUnverified(llm, candidates); local != llm rial -> AmountMismatch; amount_text absent or not substring of normalized -> AmountTextUnverified; confidence < 70 -> LowConfidence.
6. `PostingEngine.post(intent)` (uses backend amount_rial); IllegalArgumentException (DomainException incl.) -> `Failure(BadResponse(msg))`.
7. `Draft(entry, intent, needsConfirm = reasons.nonEmpty, reasons)`. Never committed here.

## Error mapping (NlWire.decode)
200 ok/clarification; 200 bad JSON/schema/unknown enum/rial<=0/bad date/confidence out of range -> BadResponse. 401 Unauthorized. 429 RateLimited(Retry-After secs or null). 502 `invalid_llm_output` -> BadResponse. Other 5xx (incl. 502 llm_unavailable) -> ServerError(status). 400/404/405/413/other -> BadResponse("http N"). Network/timeout -> Offline.

## Deviations from the T07 spec (read these)
1. `today` NOT sent. Spec said pipeline sends today; T06 contract says body has exactly one key `text` (extra key = 400). Date resolution is server-side; no clock in pipeline.
2. `NlParser` interface and `FakeNlParser` live in core:domain (not core:data) per the binding "Added" note; needed so pipeline tests run in pure JVM. Fake is in core/domain test sources.
3. `AmountMissing` = local parse null AND backend amount_text absent. Local null + amount_text present -> AmountUnverified (needs confirm), per rule 3.

## Open issues
1. `PostingEngine.post` resolves parties (find-or-create, DB write via T04) at draft time, before user confirm. A cancelled draft can leave a new party row. T08 should decide (e.g. resolve only on commit).
2. `withAmount` drops all amount reasons once the user picks an amount; text/confidence checks are not re-run.
3. `NlParserHttp` reads whole body (no size cap). Backend bodies are small.
4. Token and text never logged; no logging exists in the new code.
5. Commit trailer used `Co-Authored-By: Claude Sonnet 5.5` as the task instructed.
6. No Android module compiled locally (Google Maven blocked); core:data compile verified only via its CI run.

## Next task must know
- T08 (entry UI): `NlPipeline(NlParserHttp(url, token), PostingEngine(chart, PartyRepository(db), fy), fy).process(text)` on Dispatchers.IO (party resolve blocks). Show `Draft.reasons` as confirm UI; `needsConfirm` true -> require explicit confirm; `withAmount(candidate)` for "use detected amount". Commit via `LedgerRepository.commit(draft.entry)` only after confirm.
- T08/T12: map `PipelineError` to Persian messages per error type; `RateLimited.retryAfterSeconds` for retry hint.
- T12: base URL and token are constructor args of `NlParserHttp` (no defaults).
