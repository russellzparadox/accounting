package com.example.accounting.core.data.speech

import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeechSourceTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun cloudStubRefusesStartAndEmitsNothing() = runBlocking {
        val stub = CloudSpeechSourceStub()

        assertEquals(SpeechStartResult.UNSUPPORTED, stub.start())
        stub.stop()
        stub.release()
        assertTrue(stub.events.toList().isEmpty())
    }

    @Test
    fun startWithoutRecordAudioPermissionIsRefused() {
        // Robolectric does not grant RECORD_AUDIO by default.
        assertEquals(SpeechStartResult.NO_PERMISSION, AndroidSpeechSource(context).start())
    }

    @Test
    fun recognizerIntentIsFaIrWithPartialsAndThreeResults() {
        val intent = AndroidSpeechSource.recognizerIntent(onDevice = true)

        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals("fa-IR", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
        assertEquals(3, intent.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 0))
    }

    @Test
    fun preferOfflineOnlyForDefaultRecognizer() {
        assertFalse(AndroidSpeechSource.recognizerIntent(onDevice = true).hasExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE))
        val default: Intent = AndroidSpeechSource.recognizerIntent(onDevice = false)
        assertTrue(default.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
    }

    @Test
    fun recognizerErrorCodesMapToUserFacingKinds() {
        fun map(code: Int) = AndroidSpeechSource.mapRecognizerError(code)
        assertEquals(SpeechErrorKind.NO_MATCH, map(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(SpeechErrorKind.NO_MATCH, map(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals(SpeechErrorKind.BUSY, map(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertEquals(SpeechErrorKind.BUSY, map(10)) // ERROR_TOO_MANY_REQUESTS
        assertEquals(SpeechErrorKind.NO_PERMISSION, map(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertEquals(SpeechErrorKind.NETWORK, map(SpeechRecognizer.ERROR_NETWORK))
        assertEquals(SpeechErrorKind.NETWORK, map(SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
        assertEquals(SpeechErrorKind.NETWORK, map(SpeechRecognizer.ERROR_SERVER))
        assertEquals(SpeechErrorKind.LANGUAGE_PACK_MISSING, map(12)) // ERROR_LANGUAGE_NOT_SUPPORTED
        assertEquals(SpeechErrorKind.LANGUAGE_PACK_MISSING, map(13)) // ERROR_LANGUAGE_UNAVAILABLE
        assertEquals(SpeechErrorKind.NETWORK, map(11)) // ERROR_SERVER_DISCONNECTED
        assertEquals(SpeechErrorKind.OTHER, map(SpeechRecognizer.ERROR_AUDIO))
        assertEquals(SpeechErrorKind.OTHER, map(SpeechRecognizer.ERROR_CLIENT))
    }
}
