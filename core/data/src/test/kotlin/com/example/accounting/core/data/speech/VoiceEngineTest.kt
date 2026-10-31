package com.example.accounting.core.data.speech

import android.content.Context
import com.example.accounting.core.data.config.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Voice engine setting and cloud consent are persisted; unknown values fall back to AUTO. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceEngineTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun defaultIsAutoAndConsentIsNotGiven() {
        val settings = SettingsRepository(context)
        assertEquals(VoiceEngine.AUTO, settings.voiceEngine.value)
        assertFalse(settings.cloudSttConsented)
    }

    @Test
    fun choiceAndConsentSurviveAReload() {
        val first = SettingsRepository(context)
        first.setVoiceEngine(VoiceEngine.CLOUD_ONLY)
        first.acceptCloudStt()

        val reloaded = SettingsRepository(context)
        assertEquals(VoiceEngine.CLOUD_ONLY, reloaded.voiceEngine.value)
        assertTrue(reloaded.cloudSttConsented)
        reloaded.setVoiceEngine(VoiceEngine.AUTO)
    }

    @Test
    fun unknownStoredNameDecodesToAuto() {
        assertEquals(VoiceEngine.AUTO, VoiceEngine.decode(null))
        assertEquals(VoiceEngine.AUTO, VoiceEngine.decode("SOMETHING_ELSE"))
        assertEquals(VoiceEngine.DEVICE_ONLY, VoiceEngine.decode("DEVICE_ONLY"))
    }
}
