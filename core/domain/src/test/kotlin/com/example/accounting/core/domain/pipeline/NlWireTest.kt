package com.example.accounting.core.domain.pipeline

import com.example.accounting.core.domain.PaidWith
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.persian.AmountUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NlWireTest {
    private val okIntent = """
        {"type":"PURCHASE_CASH","amount_text":"500٬000 تومان","amount_value":500000,"amount_unit":"toman",
         "amount_rial":5000000,"date":"2026-10-31","paid_with":"CASH","bank_account":null,
         "party_name":null,"category":null,"memo":null,"confidence":90,"needs_clarification":null}
    """.trimIndent()

    private fun bad(result: ParserResult): PipelineError {
        assertIs<ParserResult.Error>(result)
        return result.error
    }

    @Test
    fun requestBodyHasOnlyTextKeyAndEscapes() {
        assertEquals("""{"text":"a\"b"}""", NlWire.requestBody("a\"b"))
    }

    @Test
    fun intentIsDecodedWithExtras() {
        val r = NlWire.decode(200, okIntent)
        assertIs<ParserResult.Intent>(r)
        assertEquals(TemplateId.PURCHASE_CASH, r.parsed.intent.type)
        assertEquals(5_000_000L, r.parsed.intent.amountRial)
        assertEquals("500٬000 تومان", r.parsed.amountText)
        assertEquals(500_000L, r.parsed.amountValue)
        assertEquals(AmountUnit.TOMAN, r.parsed.amountUnit)
    }

    @Test
    fun unknownKeysAreIgnoredAndExtrasMayBeAbsent() {
        val body = """{"type":"SALE_CASH","amount_rial":10,"date":"2026-10-31","paid_with":"BANK",
            "bank_account":"1201","confidence":50,"future_field":{"x":1}}"""
        val r = NlWire.decode(200, body)
        assertIs<ParserResult.Intent>(r)
        assertEquals(null, r.parsed.amountText)
        assertEquals(null, r.parsed.amountUnit)
        assertEquals(PaidWith.BANK, r.parsed.intent.paidWith)
    }

    @Test
    fun clarificationHasNoType() {
        val r = NlWire.decode(200, """{"needs_clarification":{"question":"چه مبلغی؟"}}""")
        assertEquals(ParserResult.Clarification("چه مبلغی؟"), r)
    }

    @Test
    fun clarificationWithoutQuestionIsBad() {
        assertIs<PipelineError.BadResponse>(bad(NlWire.decode(200, """{"needs_clarification":{}}""")))
    }

    @Test
    fun malformedOrInvalid200BodiesAreBadResponse() {
        val cases = listOf(
            "oops",
            "[]",
            okIntent.replace("PURCHASE_CASH", "NOT_A_TYPE"),
            okIntent.replace("\"amount_rial\":5000000", "\"amount_rial\":0"),
            okIntent.replace("2026-10-31", "2026-13-45"),
            okIntent.replace("\"confidence\":90", "\"confidence\":101"),
            okIntent.replace("\"amount_unit\":\"toman\"", "\"amount_unit\":\"gold\""),
            okIntent.replace("\"amount_value\":500000", "\"amount_value\":\"abc\""),
            okIntent.replace("\"amount_text\":\"500٬000 تومان\"", "\"amount_text\":42"),
            "",
        )
        for (c in cases) assertIs<PipelineError.BadResponse>(bad(NlWire.decode(200, c)), c)
    }

    @Test
    fun status401IsUnauthorized() {
        assertEquals(PipelineError.Unauthorized, bad(NlWire.decode(401, """{"error":"unauthorized"}""")))
    }

    @Test
    fun status429CarriesRetryAfter() {
        assertEquals(PipelineError.RateLimited(7), bad(NlWire.decode(429, "", " 7 ")))
        assertEquals(PipelineError.RateLimited(null), bad(NlWire.decode(429, "", "soon")))
        assertEquals(PipelineError.RateLimited(null), bad(NlWire.decode(429, "")))
    }

    @Test
    fun serverErrorsMapToServerError() {
        assertEquals(PipelineError.ServerError(500), bad(NlWire.decode(500, """{"error":"internal"}""")))
        assertEquals(PipelineError.ServerError(503), bad(NlWire.decode(503, "")))
        assertEquals(PipelineError.ServerError(502), bad(NlWire.decode(502, """{"error":"llm_unavailable"}""")))
        assertEquals(PipelineError.ServerError(502), bad(NlWire.decode(502, "")))
    }

    @Test
    fun status502InvalidLlmOutputIsBadResponse() {
        assertIs<PipelineError.BadResponse>(bad(NlWire.decode(502, """{"error":"invalid_llm_output"}""")))
    }

    @Test
    fun clientErrorsAreBadResponse() {
        for (status in listOf(400, 404, 405, 413, 418)) {
            assertTrue(bad(NlWire.decode(status, "")) is PipelineError.BadResponse, "status $status")
        }
    }
}
