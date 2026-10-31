package com.example.accounting.core.data.speech

import com.example.accounting.core.data.config.ConfigStore
import com.example.accounting.core.data.config.SettingsRepository

/** Which engines the voice input may use. AUTO = on-device, then system, then cloud when those fail. */
enum class VoiceEngine {
    AUTO,
    DEVICE_ONLY,
    CLOUD_ONLY,
    ;

    companion object {
        /** Unknown or missing stored value = AUTO (the default). */
        fun decode(name: String?): VoiceEngine = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/** Read side of the voice settings, as the routing source and the voice view model need them. */
interface VoicePreferences {
    val engine: VoiceEngine

    /** A backend URL and token are saved, so the cloud engine can run. */
    val cloudConfigured: Boolean

    /** The user has seen the cloud consent message and accepted it once. */
    val cloudConsented: Boolean

    fun acceptCloudConsent()
}

/** [VoicePreferences] backed by the app settings and the backend config. */
class SettingsVoicePreferences(
    private val settings: SettingsRepository,
    private val backend: ConfigStore,
) : VoicePreferences {
    override val engine: VoiceEngine get() = settings.voiceEngine.value
    override val cloudConfigured: Boolean get() = backend.current.isConfigured
    override val cloudConsented: Boolean get() = settings.cloudSttConsented
    override fun acceptCloudConsent() = settings.acceptCloudStt()
}
