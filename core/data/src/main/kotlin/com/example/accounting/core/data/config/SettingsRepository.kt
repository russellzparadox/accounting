package com.example.accounting.core.data.config

import android.content.Context
import com.example.accounting.core.domain.settings.AppSettings
import com.example.accounting.core.domain.settings.AppSettingsCodec
import com.example.accounting.core.data.speech.VoiceEngine
import com.example.accounting.core.persian.AmountUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Non-secret settings: amount unit, fiscal year, onboarding flag. Stored in plain SharedPreferences.
 * Lives in core:data (not feature:settings) because the ledger, entry and reports features read the unit too.
 * The backend token is NOT here: see [ConfigStore].
 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    /** Latest settings. Emits the current value on collect and after every change. */
    val settings: StateFlow<AppSettings> = state.asStateFlow()

    private val voiceEngineState = MutableStateFlow(VoiceEngine.decode(prefs.getString(KEY_VOICE_ENGINE, null)))

    /** Voice input engine: AUTO by default. Emits on change. */
    val voiceEngine: StateFlow<VoiceEngine> = voiceEngineState.asStateFlow()

    /** The user has accepted the one-time cloud speech consent. Not reset by app updates. */
    val cloudSttConsented: Boolean get() = prefs.getBoolean(KEY_CLOUD_STT_CONSENT, false)

    /** Synchronous read for ViewModels that act on a click (save, format). */
    val current: AppSettings get() = state.value

    fun setAmountUnit(unit: AmountUnit) {
        prefs.edit().putString(KEY_UNIT, AppSettingsCodec.unitName(unit)).apply()
        publish()
    }

    /** Null clears the choice (the caller then shows the current Jalali year). */
    fun setFiscalYear(year: Int?) {
        val editor = prefs.edit()
        if (year == null) editor.remove(KEY_FISCAL_YEAR) else editor.putInt(KEY_FISCAL_YEAR, year)
        editor.apply()
        publish()
    }

    fun setOnboardingAccepted(accepted: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING, accepted).apply()
        publish()
    }

    fun setVoiceEngine(engine: VoiceEngine) {
        prefs.edit().putString(KEY_VOICE_ENGINE, engine.name).apply()
        voiceEngineState.value = engine
    }

    fun acceptCloudStt() {
        prefs.edit().putBoolean(KEY_CLOUD_STT_CONSENT, true).apply()
    }

    private fun publish() {
        state.value = read()
    }

    private fun read(): AppSettings = AppSettingsCodec.decode(
        unitName = prefs.getString(KEY_UNIT, null),
        fiscalYear = if (prefs.contains(KEY_FISCAL_YEAR)) prefs.getInt(KEY_FISCAL_YEAR, 0) else null,
        onboardingAccepted = prefs.getBoolean(KEY_ONBOARDING, false),
    )

    private companion object {
        const val PREFS_NAME = "app_settings"
        const val KEY_UNIT = "amount_unit"
        const val KEY_FISCAL_YEAR = "fiscal_year"
        const val KEY_ONBOARDING = "onboarding_accepted"
        const val KEY_VOICE_ENGINE = "voice_engine"
        const val KEY_CLOUD_STT_CONSENT = "cloud_stt_consent"
    }
}
