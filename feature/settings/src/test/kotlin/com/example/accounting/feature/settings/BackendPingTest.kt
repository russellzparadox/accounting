package com.example.accounting.feature.settings

import com.example.accounting.core.data.config.BackendConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BackendPingTest {
    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun config(token: String = "tok-123") = BackendConfig(server.url("/").toString(), token)

    @Test
    fun statusCodesMapToResults() {
        assertEquals(ConnectionResult.OK, BackendPing.forStatus(200))
        assertEquals(ConnectionResult.UNAUTHORIZED, BackendPing.forStatus(401))
        assertEquals(ConnectionResult.UNAUTHORIZED, BackendPing.forStatus(403))
        assertEquals(ConnectionResult.RATE_LIMITED, BackendPing.forStatus(429))
        assertEquals(ConnectionResult.SERVER_ERROR, BackendPing.forStatus(502))
    }

    @Test
    fun checkCallsModelsWithBearerTokenAndReportsOk() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = BackendPing.check(config())

        assertEquals(ConnectionResult.OK, result)
        val request = server.takeRequest()
        assertEquals("/v1/models", request.path)
        assertEquals("GET", request.method)
        assertEquals("Bearer tok-123", request.getHeader("Authorization"))
    }

    @Test
    fun rejectedTokenIsReportedAsUnauthorized() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(ConnectionResult.UNAUTHORIZED, BackendPing.check(config()))
    }

    @Test
    fun serverErrorIsReported() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(ConnectionResult.SERVER_ERROR, BackendPing.check(config()))
    }

    @Test
    fun unreachableServerIsReported() = runBlocking {
        val unreachable = config()
        server.shutdown()
        assertEquals(ConnectionResult.UNREACHABLE, BackendPing.check(unreachable))
    }

    @Test
    fun blankConfigIsNotConfiguredAndNoRequestIsSent() = runBlocking {
        assertEquals(ConnectionResult.NOT_CONFIGURED, BackendPing.check(BackendConfig.NOT_CONFIGURED))
        assertEquals(0, server.requestCount)
    }
}
