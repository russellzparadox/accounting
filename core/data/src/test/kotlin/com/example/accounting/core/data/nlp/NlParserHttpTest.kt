package com.example.accounting.core.data.nlp

import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.domain.pipeline.ParserResult
import com.example.accounting.core.domain.pipeline.PipelineError
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NlParserHttpTest {
    private lateinit var server: MockWebServer

    private val intentJson = """
        {"type":"PURCHASE_CASH","amount_text":"500٬000 تومان","amount_value":500000,"amount_unit":"toman",
         "amount_rial":5000000,"date":"2026-10-31","paid_with":"CASH","bank_account":null,
         "party_name":null,"category":null,"memo":null,"confidence":90,"needs_clarification":null}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(timeoutMs: Int = 30_000) = NlParserHttp(
        baseUrl = server.url("/").toString(),
        token = "tok-123",
        connectTimeoutMs = timeoutMs,
        readTimeoutMs = timeoutMs,
    )

    private fun parse(text: String, timeoutMs: Int = 30_000): ParserResult =
        runBlocking { client(timeoutMs).parse(text) }

    @Test
    fun postsTextWithBearerAndDecodesIntent() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(intentJson))

        val r = parse("4 قوطی نوشابه 500٬000 تومان")

        assertTrue(r is ParserResult.Intent)
        val intent = (r as ParserResult.Intent).parsed.intent
        assertEquals(TemplateId.PURCHASE_CASH, intent.type)
        assertEquals(5_000_000L, intent.amountRial)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/parse", req.path)
        assertEquals("Bearer tok-123", req.getHeader("Authorization"))
        assertEquals("""{"text":"4 قوطی نوشابه 500٬000 تومان"}""", req.body.readUtf8())
    }

    @Test
    fun clarificationIsReturned() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"needs_clarification":{"question":"چه مبلغی؟"}}"""))
        assertEquals(ParserResult.Clarification("چه مبلغی؟"), parse("نوشابه خریدم"))
    }

    @Test
    fun status401MapsToUnauthorized() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        assertEquals(ParserResult.Error(PipelineError.Unauthorized), parse("x"))
    }

    @Test
    fun status429MapsToRateLimitedWithRetryAfter() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "7").setBody("""{"error":"rate_limited"}"""))
        assertEquals(ParserResult.Error(PipelineError.RateLimited(7)), parse("x"))
    }

    @Test
    fun status500MapsToServerError() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"internal"}"""))
        assertEquals(ParserResult.Error(PipelineError.ServerError(500)), parse("x"))
    }

    @Test
    fun status503MapsToServerError() {
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(ParserResult.Error(PipelineError.ServerError(503)), parse("x"))
    }

    @Test
    fun status502LlmUnavailableMapsToServerError() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("""{"error":"llm_unavailable"}"""))
        assertEquals(ParserResult.Error(PipelineError.ServerError(502)), parse("x"))
    }

    @Test
    fun status502InvalidLlmOutputMapsToBadResponse() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("""{"error":"invalid_llm_output"}"""))
        val r = parse("x")
        assertTrue(r is ParserResult.Error && r.error is PipelineError.BadResponse)
    }

    @Test
    fun malformedSuccessBodyMapsToBadResponse() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>oops</html>"))
        val r = parse("x")
        assertTrue(r is ParserResult.Error && r.error is PipelineError.BadResponse)
    }

    @Test
    fun connectionFailureMapsToOffline() {
        val dead = MockWebServer()
        dead.start()
        val url = dead.url("/").toString()
        dead.shutdown()
        val r = runBlocking { NlParserHttp(url, "tok").parse("x") }
        assertEquals(ParserResult.Error(PipelineError.Offline), r)
    }

    @Test
    fun readTimeoutMapsToOffline() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(ParserResult.Error(PipelineError.Offline), parse("x", timeoutMs = 300))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankTokenIsRejectedAtConstruction() {
        NlParserHttp("http://localhost:1", "  ")
    }
}
