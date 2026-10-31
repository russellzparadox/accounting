package com.example.accounting.core.data.config

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Backend endpoint and proxy token. Blank fields mean not configured. */
data class BackendConfig(val baseUrl: String, val token: String) {
    /** http(s) URL and a non-blank token. [NlParserHttp] needs both. */
    val isConfigured: Boolean
        get() = (baseUrl.startsWith("https://") || baseUrl.startsWith("http://")) && token.isNotBlank()

    companion object {
        val NOT_CONFIGURED = BackendConfig("", "")
    }
}

/**
 * SharedPreferences-backed backend config. Plain prefs for v1.
 * Follow-up (T12): move the token to EncryptedSharedPreferences.
 */
class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    /** Current config. Emits the latest value on collect and after every [save]. */
    val config: Flow<BackendConfig> = state.asStateFlow()

    fun save(config: BackendConfig) {
        val normalized = BackendConfig(config.baseUrl.trim(), config.token.trim())
        prefs.edit()
            .putString(KEY_BASE_URL, normalized.baseUrl)
            .putString(KEY_TOKEN, normalized.token)
            .apply()
        state.value = normalized
    }

    private fun read(): BackendConfig = BackendConfig(
        baseUrl = prefs.getString(KEY_BASE_URL, null).orEmpty(),
        token = prefs.getString(KEY_TOKEN, null).orEmpty(),
    )

    private companion object {
        const val PREFS_NAME = "backend_config"
        const val KEY_BASE_URL = "base_url"
        const val KEY_TOKEN = "token"
    }
}
