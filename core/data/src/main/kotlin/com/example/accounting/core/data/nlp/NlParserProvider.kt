package com.example.accounting.core.data.nlp

import com.example.accounting.core.data.config.BackendConfig
import com.example.accounting.core.domain.pipeline.NlParser

/** Parser for the current backend config, or null when the config is not usable. */
interface NlParserProvider {
    fun parserFor(config: BackendConfig): NlParser?
}

/**
 * Builds [NlParserHttp] from the config and reuses it until the config changes.
 * Returns null when the config is incomplete or the URL cannot be used.
 */
class NlParserHttpProvider : NlParserProvider {
    private val lock = Any()
    private var cachedConfig: BackendConfig? = null
    private var cachedParser: NlParser? = null

    override fun parserFor(config: BackendConfig): NlParser? = synchronized(lock) {
        if (!config.isConfigured) return null
        if (config != cachedConfig) {
            cachedParser = runCatching { NlParserHttp(config.baseUrl, config.token) }.getOrNull()
            cachedConfig = config
        }
        cachedParser
    }
}
