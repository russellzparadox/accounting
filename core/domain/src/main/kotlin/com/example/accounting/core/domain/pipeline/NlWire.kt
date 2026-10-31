package com.example.accounting.core.domain.pipeline

import com.example.accounting.core.domain.TransactionIntent
import com.example.accounting.core.domain.domainJson
import com.example.accounting.core.persian.AmountUnit
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Wire contract of `POST /v1/parse` (docs/BACKEND.md). Pure: no I/O. */
object NlWire {
    /** Request body. Exactly one key, `text`. `today` is server-side and must not be sent. */
    fun requestBody(text: String): String = buildJsonObject { put("text", text) }.toString()

    /** Maps an HTTP response to a [ParserResult]. [retryAfter] is the raw Retry-After header value. */
    fun decode(status: Int, body: String, retryAfter: String? = null): ParserResult = when (status) {
        200 -> decodeOk(body)
        401 -> ParserResult.Error(PipelineError.Unauthorized)
        429 -> ParserResult.Error(PipelineError.RateLimited(retryAfter?.trim()?.toLongOrNull()))
        400, 404, 405, 413 -> ParserResult.Error(PipelineError.BadResponse("http $status"))
        502 -> if (errorCode(body) == "invalid_llm_output") {
            ParserResult.Error(PipelineError.BadResponse("invalid_llm_output"))
        } else {
            ParserResult.Error(PipelineError.ServerError(502))
        }
        in 500..599 -> ParserResult.Error(PipelineError.ServerError(status))
        else -> ParserResult.Error(PipelineError.BadResponse("http $status"))
    }

    private fun decodeOk(body: String): ParserResult {
        return try {
            val root = domainJson.parseToJsonElement(body) as? JsonObject ?: return bad("not an object")
            val nc = root["needs_clarification"]
            if (nc != null && nc !is JsonNull) {
                val question = (nc as? JsonObject)?.get("question")?.stringOrNull()
                    ?: return bad("clarification without question")
                return ParserResult.Clarification(question)
            }
            val intent = domainJson.decodeFromJsonElement(TransactionIntent.serializer(), root)
            if (intent.needsClarification != null) return bad("unexpected clarification field")
            val amountValue = root["amount_value"]?.takeUnless { it is JsonNull }?.let {
                (it as? JsonPrimitive)?.longOrNull ?: throw IllegalArgumentException("amount_value")
            }
            ParserResult.Intent(
                ParsedIntent(
                    intent = intent,
                    amountText = root.string("amount_text"),
                    amountValue = amountValue,
                    amountUnit = unitOf(root.string("amount_unit")),
                ),
            )
        } catch (e: Exception) {
            bad("schema: ${e::class.simpleName}")
        }
    }

    private fun bad(reason: String) = ParserResult.Error(PipelineError.BadResponse(reason))

    private fun unitOf(raw: String?): AmountUnit? = when (raw) {
        null -> null
        "toman" -> AmountUnit.TOMAN
        "rial" -> AmountUnit.RIAL
        else -> throw IllegalArgumentException("unknown amount_unit")
    }

    /** String field: absent or null -> null; non-string -> throws (caught as BadResponse). */
    private fun JsonObject.string(key: String): String? = this[key]?.takeUnless { it is JsonNull }?.let {
        (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: throw IllegalArgumentException(key)
    }

    private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun errorCode(body: String): String? = try {
        (domainJson.parseToJsonElement(body) as? JsonObject)?.get("error")?.stringOrNull()
    } catch (e: Exception) {
        null
    }
}
