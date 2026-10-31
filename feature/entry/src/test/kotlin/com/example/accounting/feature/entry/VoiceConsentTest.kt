package com.example.accounting.feature.entry

import com.example.accounting.core.data.speech.SpeechDiagnostics
import com.example.accounting.core.data.speech.SpeechEngine
import com.example.accounting.core.data.speech.SpeechErrorKind
import com.example.accounting.core.data.speech.SpeechEvent
import com.example.accounting.core.data.speech.SpeechStartResult
import com.example.accounting.core.data.speech.VoiceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

/** Cloud consent gate, processing and engine label in VoiceViewModel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceConsentTest {
    private val speech = FakeSpeechSource()
    private val prefs = FakeVoicePreferences(engine = VoiceEngine.AUTO, cloudConfigured = true, cloudConsented = false)
    private val vm = VoiceViewModel(speech, SpeechDiagnostics(), FakeLanguagePack(), prefs)

    private fun emit(event: SpeechEvent) {
        speech.emit(event)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun firstCloudCapableStartAsksForConsentAndStartsNothing() {
        vm.onAction(VoiceAction.Start("متن"))
        assertTrue(vm.state.value.consentRequired)
        assertFalse(vm.state.value.listening)
        assertEquals(0, speech.starts)
    }

    @Test
    fun declinedConsentStartsNothingAndKeepsTheText() {
        vm.onAction(VoiceAction.Start("متن"))
        vm.onAction(VoiceAction.DismissCloudConsent)
        assertFalse(vm.state.value.consentRequired)
        assertEquals(0, speech.starts)
        assertEquals(0, prefs.consentAccepted)
    }

    @Test
    fun confirmedConsentIsRememberedAndTheWaitingSessionStarts() {
        vm.onAction(VoiceAction.Start("۲ عدد"))
        vm.onAction(VoiceAction.ConfirmCloudConsent)

        assertEquals(1, prefs.consentAccepted)
        assertEquals(1, speech.starts)
        assertTrue(vm.state.value.listening)
        assertFalse(vm.state.value.consentRequired)
        emit(SpeechEvent.Partial("نوشابه"))
        assertEquals("۲ عدد نوشابه", vm.state.value.transcript?.text)
    }

    @Test
    fun noConsentPromptWhenAlreadyConsentedOrDeviceOnly() {
        prefs.cloudConsented = true
        vm.onAction(VoiceAction.Start(""))
        assertEquals(1, speech.starts)
        assertFalse(vm.state.value.consentRequired)
    }

    @Test
    fun deviceOnlyNeverAsksForConsent() {
        prefs.engine = VoiceEngine.DEVICE_ONLY
        vm.onAction(VoiceAction.Start(""))
        assertEquals(1, speech.starts)
        assertFalse(vm.state.value.consentRequired)
    }

    @Test
    fun noBackendMeansNoConsentPrompt() {
        prefs.cloudConfigured = false
        vm.onAction(VoiceAction.Start(""))
        assertEquals(1, speech.starts)
    }

    @Test
    fun engineLabelFollowsTheEngineThatIsRunning() {
        prefs.cloudConsented = true
        vm.onAction(VoiceAction.Start(""))
        emit(SpeechEvent.EngineChanged(SpeechEngine.SYSTEM))
        assertEquals(SpeechEngine.SYSTEM, vm.state.value.engine)
        emit(SpeechEvent.EngineChanged(SpeechEngine.CLOUD))
        assertEquals(SpeechEngine.CLOUD, vm.state.value.engine)
    }

    @Test
    fun processingReplacesListeningAndFinalEndsIt() {
        vm.onAction(VoiceAction.Start(""))
        emit(SpeechEvent.Processing)
        assertTrue(vm.state.value.processing)
        assertFalse(vm.state.value.listening)

        emit(SpeechEvent.Final("نوشابه"))
        assertFalse(vm.state.value.processing)
        assertEquals("نوشابه", vm.state.value.transcript?.text)
    }

    @Test
    fun cloudErrorsAreTypedAndEndProcessing() {
        vm.onAction(VoiceAction.Start(""))
        emit(SpeechEvent.Processing)
        emit(SpeechEvent.Error(SpeechErrorKind.CLOUD_RATE_LIMITED))
        assertEquals(SpeechErrorKind.CLOUD_RATE_LIMITED, vm.state.value.error)
        assertFalse(vm.state.value.processing)
        assertFalse(vm.state.value.listening)
    }

    @Test
    fun notConfiguredStartIsMappedToItsOwnError() {
        prefs.engine = VoiceEngine.DEVICE_ONLY
        speech.startResult = SpeechStartResult.NOT_CONFIGURED
        vm.onAction(VoiceAction.Start(""))
        assertEquals(SpeechErrorKind.CLOUD_NOT_CONFIGURED, vm.state.value.error)
        assertFalse(vm.state.value.listening)
    }
}
