package com.example.accounting.core.data.config

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Backend endpoint and proxy token. Blank fields mean not configured. The token is the proxy token, never an LLM key. */
data class BackendConfig(val baseUrl: String, val token: String) {
    /** http(s) URL and a non-blank token. [NlParserHttp] needs both. */
    val isConfigured: Boolean
        get() = (baseUrl.startsWith("https://") || baseUrl.startsWith("http://")) && token.isNotBlank()

    /** Hides the token in logs and debugger output. */
    override fun toString(): String = "BackendConfig(baseUrl=$baseUrl, token=${if (token.isBlank()) "" else "***"})"

    companion object {
        val NOT_CONFIGURED = BackendConfig("", "")
    }
}

/**
 * SharedPreferences-backed backend config. The URL is plain; the token is stored encrypted with [cipher].
 * Migration: a token saved by an earlier build in plain text is encrypted on first read and the plain copy removed.
 */
class ConfigStore(
    context: Context,
    private val cipher: TokenCipher = KeystoreTokenCipher(),
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    // Migration runs first and writes prefs only (not [state], which is not initialised yet).
    private val state = MutableStateFlow(run { migrateLegacyToken(); read() })

    /** Current config. Emits the latest value on collect and after every [save]. */
    val config: Flow<BackendConfig> = state.asStateFlow()

    /** Synchronous read of the latest config, for click-time decisions (voice engine routing). */
    val current: BackendConfig get() = state.value

    fun save(config: BackendConfig) {
        val normalized = BackendConfig(config.baseUrl.trim(), config.token.trim())
        prefs.edit()
            .putString(KEY_BASE_URL, normalized.baseUrl)
            .putString(KEY_TOKEN_ENC, if (normalized.token.isEmpty()) "" else cipher.encrypt(normalized.token))
            .remove(KEY_TOKEN_PLAIN_LEGACY)
            .apply()
        state.value = normalized
    }

    /** Earlier build stored the token in plain text. Encrypt it and drop the plain copy. */
    private fun migrateLegacyToken() {
        val legacy = prefs.getString(KEY_TOKEN_PLAIN_LEGACY, null) ?: return
        val token = legacy.trim()
        prefs.edit()
            .putString(KEY_TOKEN_ENC, if (token.isEmpty()) "" else cipher.encrypt(token))
            .remove(KEY_TOKEN_PLAIN_LEGACY)
            .apply()
    }

    private fun read(): BackendConfig {
        val stored = prefs.getString(KEY_TOKEN_ENC, null).orEmpty()
        val token = if (stored.isEmpty()) "" else cipher.decrypt(stored).orEmpty()
        return BackendConfig(
            baseUrl = prefs.getString(KEY_BASE_URL, null).orEmpty(),
            token = token,
        )
    }

    private companion object {
        const val PREFS_NAME = "backend_config"
        const val KEY_BASE_URL = "base_url"
        const val KEY_TOKEN_ENC = "token_enc"
        const val KEY_TOKEN_PLAIN_LEGACY = "token"
    }
}
