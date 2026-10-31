package com.example.accounting.feature.settings

import com.example.accounting.core.data.config.BackendConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outcome of «تست اتصال». Mapped to Persian text in the screen. */
enum class ConnectionResult { OK, UNAUTHORIZED, RATE_LIMITED, SERVER_ERROR, UNREACHABLE, NOT_CONFIGURED }

/**
 * Lightweight authenticated check: GET {base}/v1/models with the proxy token. Lists no secrets; the token is sent
 * only in the Authorization header and is never logged or shown.
 */
object BackendPing {
    private const val TIMEOUT_MS = 10_000

    /** Pure mapping, so the status rules are testable without a network. */
    fun forStatus(code: Int): ConnectionResult = when (code) {
        in 200..299 -> ConnectionResult.OK
        401, 403 -> ConnectionResult.UNAUTHORIZED
        429 -> ConnectionResult.RATE_LIMITED
        else -> ConnectionResult.SERVER_ERROR
    }

    suspend fun check(config: BackendConfig): ConnectionResult = withContext(Dispatchers.IO) {
        if (!config.isConfigured) return@withContext ConnectionResult.NOT_CONFIGURED
        try {
            val url = URL(config.baseUrl.trimEnd('/') + "/v1/models")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Authorization", "Bearer " + config.token)
                forStatus(connection.responseCode)
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            ConnectionResult.UNREACHABLE
        } catch (e: IllegalArgumentException) {
            ConnectionResult.UNREACHABLE
        }
    }
}
