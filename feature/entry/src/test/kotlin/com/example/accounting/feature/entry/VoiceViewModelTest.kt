package com.example.accounting.feature.entry

import com.example.accounting.core.data.speech.SpeechErrorKind
import com.example.accounting.core.data.speech.SpeechEvent
import com.example.accounting.core.data.speech.SpeechStartResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

/** VoiceViewModel against [FakeSpeechSource]. No recognizer, no microphone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceViewModelTest {
    private val speech = FakeSpeechSource()
    private val vm = VoiceViewModel(speech)

    /** Delivers queued main-looper work, so flow emissions are visible to assertions. */
    private fun emit(event: SpeechEvent) {
        speech.emit(event)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun startListensAndPartialsFillTheText() {
        vm.onAction(VoiceAction.Start(""))

        assertTrue(vm.state.value.listening)
        assertEquals(1, speech.starts)

        emit(SpeechEvent.Partial("۴ قوطی نوشابه"))

        assertEquals("۴ قوطی نوشابه", vm.state.value.transcript?.text)
        assertTrue(vm.state.value.listening)
    }

    @Test
    fun recognizedTextIsAppendedToTextTypedBeforeSpeaking() {
        vm.onAction(VoiceAction.Start("  ۲ عدد  "))

        emit(SpeechEvent.Partial("نوشابه"))

        assertEquals("۲ عدد نوشابه", vm.state.value.transcript?.text)
    }

    @Test
    fun finalTextStaysInTheFieldAndListeningEnds() {
        vm.onAction(VoiceAction.Start(""))
        emit(SpeechEvent.Partial("۴ قوطی"))

        emit(SpeechEvent.Final("۴ قوطی نوشابه روی هم پانصد هزار تومان خریدم"))

        assertEquals("۴ قوطی نوشابه روی هم پانصد هزار تومان خریدم", vm.state.value.transcript?.text)
        assertFalse(vm.state.value.listening)
        assertNull(vm.state.value.error)
    }

    @Test
    fun noMatchKeepsPartialTextAndShowsError() {
        vm.onAction(VoiceAction.Start(""))
        emit(SpeechEvent.Partial("سلام"))

        emit(SpeechEvent.Error(SpeechErrorKind.NO_MATCH))

        assertEquals("سلام", vm.state.value.transcript?.text)
        assertEquals(SpeechErrorKind.NO_MATCH, vm.state.value.error)
        assertFalse(vm.state.value.listening)
    }

    @Test
    fun startFailuresAreMappedToErrorsWithoutListening() {
        val expected = mapOf(
            SpeechStartResult.BUSY to SpeechErrorKind.BUSY,
            SpeechStartResult.UNAVAILABLE to SpeechErrorKind.UNAVAILABLE,
            SpeechStartResult.NO_PERMISSION to SpeechErrorKind.NO_PERMISSION,
            SpeechStartResult.UNSUPPORTED to SpeechErrorKind.UNSUPPORTED,
        )
        for ((result, kind) in expected) {
            speech.startResult = result
            vm.onAction(VoiceAction.Start(""))

            assertFalse(vm.state.value.listening)
            assertEquals(kind, vm.state.value.error)
        }
    }

    @Test
    fun startWhileListeningIsIgnored() {
        vm.onAction(VoiceAction.Start(""))
        vm.onAction(VoiceAction.Start(""))

        assertEquals(1, speech.starts)
    }

    @Test
    fun stopAsksSourceAndListeningEndsWhenResultArrives() {
        vm.onAction(VoiceAction.Start(""))

        vm.onAction(VoiceAction.Stop)
        assertEquals(1, speech.stops)
        assertTrue(vm.state.value.listening)

        emit(SpeechEvent.Final("سه قوطی"))
        assertFalse(vm.state.value.listening)
    }

    @Test
    fun successfulStartClearsPreviousError() {
        speech.startResult = SpeechStartResult.BUSY
        vm.onAction(VoiceAction.Start(""))
        assertEquals(SpeechErrorKind.BUSY, vm.state.value.error)

        speech.startResult = SpeechStartResult.STARTED
        vm.onAction(VoiceAction.Start(""))

        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.listening)
    }
}
