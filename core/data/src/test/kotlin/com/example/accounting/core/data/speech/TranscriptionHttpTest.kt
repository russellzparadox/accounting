package com.example.accounting.core.data.speech

import android.content.Context
import com.example.accounting.core.data.config.BackendConfig
import java.io.File
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Upload request shape against a MockWebServer, and the status-to-error mapping. No real network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranscriptionHttpTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var audio: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        audio = File(context.cacheDir, "stt-test.m4a").apply { writeBytes("AUDIO-PAYLOAD".toByteArray()) }
    }

    @After
    fun tearDown() {
        server.shutdown()
        audio.delete()
    }

    private fun config() = BackendConfig(server.url("/").toString().trimEnd('/'), "tok-SECRET-1")

    @Test
    fun uploadSendsBearerMultipartAndReturnsTheTrimmedText() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"text":"  نوشابه پانصد هزار تومان  "}"""))

        val result = TranscriptionHttp().transcribe(audio, config())

        assertEquals(TranscriptResult.Text("نوشابه پانصد هزار تومان"), result)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/audio/transcriptions", req.path)
        assertEquals("Bearer tok-SECRET-1", req.getHeader("Authorization"))
        val contentType = req.getHeader("Content-Type").orEmpty()
        assertTrue(contentType.startsWith("multipart/form-data; boundary="))

        val body = req.body.readUtf8()
        assertTrue(body.contains("name=\"file\"; filename=\"audio.m4a\""))
        assertTrue(body.contains("Content-Type: audio/mp4"))
        assertTrue(body.contains("AUDIO-PAYLOAD"))
        assertTrue(body.contains("name=\"language\"\r\n\r\nfa\r\n"))
        assertTrue(body.contains("name=\"response_format\"\r\n\r\njson\r\n"))
        assertFalse("client never sends a model; the server picks it", body.contains("name=\"model\""))
    }

    @Test
    fun multipartBodyIsWellFormed() {
        val boundary = "B"
        val body = String(TranscriptionHttp.buildMultipart("X".toByteArray(), boundary), Charsets.UTF_8)
        assertTrue(body.startsWith("--B\r\n"))
        assertTrue(body.endsWith("--B--\r\n"))
    }

    @Test
    fun unconfiguredNeverTouchesTheNetwork() {
        val result = TranscriptionHttp().transcribe(audio, BackendConfig.NOT_CONFIGURED)
        assertEquals(TranscriptResult.Failed(SpeechErrorKind.CLOUD_NOT_CONFIGURED), result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun unreachableServerIsUnavailable() {
        val dead = BackendConfig("http://127.0.0.1:1", "tok")
        assertEquals(TranscriptResult.Failed(SpeechErrorKind.CLOUD_UNAVAILABLE), TranscriptionHttp().transcribe(audio, dead))
    }

    @Test
    fun statusesMapToTypedErrors() {
        assertEquals(TranscriptResult.Failed(SpeechErrorKind.CLOUD_UNAUTHORIZED), TranscriptionHttp.decode(401, ""))
        assertEquals(TranscriptResult.Failed(SpeechErrorKind.CLOUD_UNAUTHORIZED), TranscriptionHttp.decode(403, ""))
        assertEquals(TranscriptResult.Failed(SpeechErrorKind.CLOUD_TOO_LARGE), TranscriptionHttp.decode(413, ""))
        assertEquals(TranscriptResult.Failed(SpeechErrorKind.CLOUD_RATE_LIMITED), TranscriptionHttp.decode(429, ""))
        assertEquals(TranscriptResult.Failed(SpeechErrorKind.CLOUD_NOT_CONFIGURED), TranscriptionHttp.decode(501, ""))
        assertEquals(TranscriptResult.Failed(SpeechErrorKind.OTHER), TranscriptionHttp.decode(400, "{}"))
        assertEquals(TranscriptResult.Failed(SpeechErrorKind.CLOUD_UNAVAILABLE), TranscriptionHttp.decode(502, ""))
        assertEquals(TranscriptResult.Failed(SpeechErrorKind.CLOUD_UNAVAILABLE), TranscriptionHttp.decode(503, ""))
    }

    @Test
    fun successWithBadOrEmptyBodyIsTypedNotCrashing() {
        assertEquals(TranscriptResult.Failed(SpeechErrorKind.OTHER), TranscriptionHttp.decode(200, "<html>"))
        assertEquals(TranscriptResult.Failed(SpeechErrorKind.NO_MATCH), TranscriptionHttp.decode(200, """{"text":"   "}"""))
    }
}
