package com.example.accounting.core.data.speech

import com.example.accounting.core.data.config.BackendConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONObject

/** Outcome of one upload: the transcript, or a typed failure the UI maps to Persian text. */
sealed interface TranscriptResult {
    data class Text(val text: String) : TranscriptResult
    data class Failed(val kind: SpeechErrorKind) : TranscriptResult
}

/** Uploads one recording and returns its transcript. Blocking: call from Dispatchers.IO. */
interface CloudTranscriber {
    fun transcribe(audio: File, config: BackendConfig): TranscriptResult
}

/**
 * `POST {baseUrl}/v1/audio/transcriptions` (multipart: file, language=fa, response_format=json), bearer token.
 * Same transport style as [com.example.accounting.core.data.nlp.NlParserHttp]. Never logs audio, text or the token.
 * Status mapping mirrors the backend: 401/403 unauthorized, 413 too large, 429 rate limited, 501 not configured,
 * other 4xx invalid audio (OTHER), 5xx and network errors unavailable.
 */
class TranscriptionHttp(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 60_000,
) : CloudTranscriber {

    override fun transcribe(audio: File, config: BackendConfig): TranscriptResult {
        if (!config.isConfigured) return TranscriptResult.Failed(SpeechErrorKind.CLOUD_NOT_CONFIGURED)
        val boundary = "----accounting${UUID.randomUUID()}"
        val body = try {
            buildMultipart(audio.readBytes(), boundary)
        } catch (e: IOException) {
            return TranscriptResult.Failed(SpeechErrorKind.OTHER)
        }
        val conn = try {
            URL(config.baseUrl.trimEnd('/') + "/v1/audio/transcriptions").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return TranscriptResult.Failed(SpeechErrorKind.CLOUD_UNAVAILABLE)
        }
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.doOutput = true
            conn.useCaches = false
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${config.token}")
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }

            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            decode(status, text)
        } catch (e: IOException) {
            TranscriptResult.Failed(SpeechErrorKind.CLOUD_UNAVAILABLE)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val FILE_NAME = "audio.m4a"
        const val FILE_MIME = "audio/mp4"

        /** Pure: the multipart body (file, language=fa, response_format=json). Exposed for tests. */
        fun buildMultipart(audio: ByteArray, boundary: String): ByteArray {
            val out = ByteArrayOutputStream()
            fun write(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
            fun field(name: String, value: String) =
                write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
            write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$FILE_NAME\"\r\n")
            write("Content-Type: $FILE_MIME\r\n\r\n")
            out.write(audio)
            write("\r\n")
            field("language", "fa")
            field("response_format", "json")
            write("--$boundary--\r\n")
            return out.toByteArray()
        }

        /** Pure: maps an HTTP status and body to a result. Exposed for tests. */
        fun decode(status: Int, body: String): TranscriptResult = when (status) {
            in 200..299 -> {
                val text = try {
                    JSONObject(body).getString("text").trim()
                } catch (e: Exception) {
                    null
                }
                when {
                    text == null -> TranscriptResult.Failed(SpeechErrorKind.OTHER)
                    text.isEmpty() -> TranscriptResult.Failed(SpeechErrorKind.NO_MATCH)
                    else -> TranscriptResult.Text(text)
                }
            }
            401, 403 -> TranscriptResult.Failed(SpeechErrorKind.CLOUD_UNAUTHORIZED)
            413 -> TranscriptResult.Failed(SpeechErrorKind.CLOUD_TOO_LARGE)
            429 -> TranscriptResult.Failed(SpeechErrorKind.CLOUD_RATE_LIMITED)
            501 -> TranscriptResult.Failed(SpeechErrorKind.CLOUD_NOT_CONFIGURED)
            in 400..499 -> TranscriptResult.Failed(SpeechErrorKind.OTHER)
            else -> TranscriptResult.Failed(SpeechErrorKind.CLOUD_UNAVAILABLE)
        }
    }
}
