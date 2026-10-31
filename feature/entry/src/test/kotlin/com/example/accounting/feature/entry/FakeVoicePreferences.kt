package com.example.accounting.feature.entry

import com.example.accounting.core.data.speech.VoiceEngine
import com.example.accounting.core.data.speech.VoicePreferences

/** Voice settings with settable values. Accepting consent flips [cloudConsented]. */
class FakeVoicePreferences(
    override var engine: VoiceEngine = VoiceEngine.AUTO,
    override var cloudConfigured: Boolean = false,
    override var cloudConsented: Boolean = false,
) : VoicePreferences {
    var consentAccepted = 0
        private set

    override fun acceptCloudConsent() {
        consentAccepted++
        cloudConsented = true
    }
}
