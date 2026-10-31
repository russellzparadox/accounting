package com.example.accounting.core.data.nlp

import com.example.accounting.core.domain.pipeline.NlParser
import com.example.accounting.core.domain.pipeline.NlWire
import com.example.accounting.core.domain.pipeline.ParserResult
import com.example.accounting.core.domain.pipeline.PipelineError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * `POST {baseUrl}/v1/parse` client (docs/BACKEND.md). Transport only; wire mapping is [NlWire].
 * Base URL and token are injected (settings UI is T12). Never logs the request text or the token.
 * Network and timeout failures map to [PipelineError.Offline]. Blocking I/O runs on Dispatchers.IO.
 */
class NlParserHttp(
    baseUrl: String,
    private val token: String,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000,
) : NlParser {
    private val endpoint: URL = URL(baseUrl.trimEnd('/') + "/v1/parse")

    init {
        require(token.isNotBlank()) { "proxy token required" }
    }

    override suspend fun parse(text: String): ParserResult = withContext(Dispatchers.IO) {
        val conn = try {
            endpoint.openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return@withContext ParserResult.Error(PipelineError.Offline)
        }
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.doOutput = true
            conn.useCaches = false
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.outputStream.use { it.write(NlWire.requestBody(text).toByteArray(Charsets.UTF_8)) }

            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            NlWire.decode(status, body, conn.getHeaderField("Retry-After"))
        } catch (e: IOException) {
            ParserResult.Error(PipelineError.Offline)
        } finally {
            conn.disconnect()
        }
    }
}
