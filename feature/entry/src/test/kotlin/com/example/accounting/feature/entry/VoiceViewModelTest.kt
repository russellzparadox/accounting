package com.example.accounting.feature.entry

import com.example.accounting.core.data.speech.PackEvent
import com.example.accounting.core.data.speech.PackStatus
import com.example.accounting.core.data.speech.SpeechDiagnostics
import com.example.accounting.core.data.speech.SpeechEngine
import com.example.accounting.core.data.speech.SpeechErrorKind
import com.example.accounting.core.data.speech.SpeechEvent
import com.example.accounting.core.data.speech.SpeechStartResult
import com.example.accounting.core.data.speech.VoiceErrorRecord
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

/** VoiceViewModel against [FakeSpeechSource] and [FakeLanguagePack]. No recognizer, no microphone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceViewModelTest {
    private val speech = FakeSpeechSource()
    private val diagnostics = SpeechDiagnostics()
    private val pack = FakeLanguagePack()
    private val vm = VoiceViewModel(speech, diagnostics, pack)

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
        assertNull(vm.state.value.errorCode)
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
            SpeechStartResult.UNAVAILABLE to SpeechErrorKind.NO_RECOGNIZER_INSTALLED,
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

    @Test
    fun languagePackErrorOffersDownloadWhenPackIsNotInstalled() {
        pack.status = PackStatus.NOT_INSTALLED
        vm.onAction(VoiceAction.Start(""))

        emit(SpeechEvent.Error(SpeechErrorKind.LANGUAGE_PACK_MISSING, 13))

        assertEquals(SpeechErrorKind.LANGUAGE_PACK_MISSING, vm.state.value.error)
        assertEquals(13, vm.state.value.errorCode)
        assertEquals(PackUi.Offer, vm.state.value.pack)
    }

    @Test
    fun queuedPackShowsPendingAndNoDownloadButton() {
        pack.status = PackStatus.PENDING
        vm.onAction(VoiceAction.Start(""))

        emit(SpeechEvent.Error(SpeechErrorKind.LANGUAGE_PACK_MISSING, 13))

        assertEquals(PackUi.Pending, vm.state.value.pack)
    }

    @Test
    fun installedPackAfterLanguageErrorOffersNothingExtra() {
        pack.status = PackStatus.INSTALLED
        vm.onAction(VoiceAction.Start(""))

        emit(SpeechEvent.Error(SpeechErrorKind.LANGUAGE_PACK_MISSING, 12))

        assertEquals(PackUi.None, vm.state.value.pack)
        assertEquals(SpeechErrorKind.LANGUAGE_PACK_MISSING, vm.state.value.error)
    }

    @Test
    fun packCheckDoesNotRunForOtherErrors() {
        pack.status = PackStatus.NOT_INSTALLED
        vm.onAction(VoiceAction.Start(""))

        emit(SpeechEvent.Error(SpeechErrorKind.NETWORK, 2))

        assertEquals(PackUi.None, vm.state.value.pack)
    }

    @Test
    fun downloadShowsProgressThenDone() {
        pack.status = PackStatus.NOT_INSTALLED
        vm.onAction(VoiceAction.Start(""))
        emit(SpeechEvent.Error(SpeechErrorKind.LANGUAGE_PACK_MISSING, 13))

        vm.onAction(VoiceAction.DownloadLanguagePack)
        assertEquals(PackUi.Downloading(null), vm.state.value.pack)

        pack.emit(PackEvent.Progress(40))
        assertEquals(PackUi.Downloading(40), vm.state.value.pack)

        pack.emit(PackEvent.Done)
        assertEquals(PackUi.Done, vm.state.value.pack)
        assertEquals(1, pack.downloads)
    }

    @Test
    fun secondDownloadTapWhileRunningIsIgnored() {
        pack.status = PackStatus.NOT_INSTALLED
        vm.onAction(VoiceAction.Start(""))
        emit(SpeechEvent.Error(SpeechErrorKind.LANGUAGE_PACK_MISSING, 13))

        vm.onAction(VoiceAction.DownloadLanguagePack)
        vm.onAction(VoiceAction.DownloadLanguagePack)

        assertEquals(1, pack.downloads)
    }

    @Test
    fun failedDownloadShowsItsCode() {
        pack.status = PackStatus.NOT_INSTALLED
        vm.onAction(VoiceAction.Start(""))
        emit(SpeechEvent.Error(SpeechErrorKind.LANGUAGE_PACK_MISSING, 13))

        vm.onAction(VoiceAction.DownloadLanguagePack)
        pack.emit(PackEvent.Failed(7))

        assertEquals(PackUi.Failed(7), vm.state.value.pack)
    }

    @Test
    fun startOrFinalResetsThePackLine() {
        pack.status = PackStatus.NOT_INSTALLED
        vm.onAction(VoiceAction.Start(""))
        emit(SpeechEvent.Error(SpeechErrorKind.LANGUAGE_PACK_MISSING, 13))

        vm.onAction(VoiceAction.Start(""))

        assertEquals(PackUi.None, vm.state.value.pack)
        assertNull(vm.state.value.errorCode)
    }

    @Test
    fun errorsAndEngineChangesAreRecordedForDiagnostics() {
        vm.onAction(VoiceAction.Start(""))

        emit(SpeechEvent.EngineChanged(SpeechEngine.ON_DEVICE))
        emit(SpeechEvent.Error(SpeechErrorKind.NETWORK, 2))

        assertEquals(VoiceErrorRecord(SpeechErrorKind.NETWORK, 2, SpeechEngine.ON_DEVICE), diagnostics.lastError.value)
    }

    @Test
    fun refusedStartIsRecordedForDiagnosticsWithoutCode() {
        speech.startResult = SpeechStartResult.UNAVAILABLE
        vm.onAction(VoiceAction.Start(""))

        assertEquals(
            VoiceErrorRecord(SpeechErrorKind.NO_RECOGNIZER_INSTALLED, null, null),
            diagnostics.lastError.value,
        )
    }
}

/** Scripted [com.example.accounting.core.data.speech.SpeechLanguagePack]. */
class FakeLanguagePack(var status: PackStatus = PackStatus.NOT_INSTALLED) :
    com.example.accounting.core.data.speech.SpeechLanguagePack {
    var downloads = 0
        private set
    private var listener: ((PackEvent) -> Unit)? = null

    override fun check(onResult: (PackStatus) -> Unit) = onResult(status)

    override fun download(onEvent: (PackEvent) -> Unit) {
        downloads++
        listener = onEvent
    }

    override fun release() = Unit

    fun emit(event: PackEvent) {
        checkNotNull(listener) { "download not started" }(event)
    }
}
