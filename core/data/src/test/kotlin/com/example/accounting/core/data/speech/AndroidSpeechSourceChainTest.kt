package com.example.accounting.core.data.speech

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fallback chain of [AndroidSpeechSource] driven by [FakeRecognizerFactory]. No device, no recognizer service. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidSpeechSourceChainTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun sourceWith(factory: FakeRecognizerFactory, permission: Boolean = true) =
        AndroidSpeechSource(context, factory, hasPermission = { permission })

    /** Collects the source's events into the returned list. Unconfined, so each emission lands at once. */
    private fun AndroidSpeechSource.collectEvents(): MutableList<SpeechEvent> {
        val list = mutableListOf<SpeechEvent>()
        CoroutineScope(Dispatchers.Unconfined).launch { events.collect { list += it } }
        return list
    }

    private fun FakeRecognizer.callbacksOrFail(): RecognizerCallbacks =
        checkNotNull(callbacks) { "attempt has no listener" }

    @Test
    fun onDeviceLanguageErrorFallsBackToSystemOnceAndSucceeds() {
        val factory = FakeRecognizerFactory(hasOnDevice = true)
        val source = sourceWith(factory)
        val events = source.collectEvents()

        assertEquals(SpeechStartResult.STARTED, source.start())
        assertTrue(factory.created[0].onDeviceEngine)
        factory.created[0].callbacksOrFail().onError(13)

        assertEquals(2, factory.created.size)
        assertTrue(factory.created[0].destroyed)
        assertFalse(factory.created[1].onDeviceEngine)
        assertEquals(1, factory.created[1].starts)
        factory.created[1].callbacksOrFail().onResults("۴ قوطی نوشابه")

        assertEquals(
            listOf(
                SpeechEvent.EngineChanged(SpeechEngine.ON_DEVICE, null),
                SpeechEvent.EngineChanged(SpeechEngine.SYSTEM, 13),
                SpeechEvent.Final("۴ قوطی نوشابه"),
            ),
            events,
        )
    }

    @Test
    fun systemAttemptUsesPreferOfflineOnlyWhenNotOnDevice() {
        val factory = FakeRecognizerFactory(hasOnDevice = true)
        val source = sourceWith(factory)
        source.collectEvents()

        source.start()
        assertFalse(factory.created[0].lastIntent!!.hasExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE))
        factory.created[0].callbacksOrFail().onError(12)

        assertTrue(factory.created[1].lastIntent!!.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
    }

    @Test
    fun everyFallbackCodeTriggersExactlyOneRetry() {
        for (code in listOf(5, 11, 12, 13)) {
            val factory = FakeRecognizerFactory(hasOnDevice = true)
            val source = sourceWith(factory)
            source.collectEvents()

            source.start()
            factory.created[0].callbacksOrFail().onError(code)

            assertEquals("code $code", 2, factory.created.size)
        }
    }

    @Test
    fun fallbackFailingWithLanguageErrorGivesTypedErrorAndNeverLoops() {
        val factory = FakeRecognizerFactory(hasOnDevice = true)
        val source = sourceWith(factory)
        val events = source.collectEvents()

        source.start()
        factory.created[0].callbacksOrFail().onError(13)
        factory.created[1].callbacksOrFail().onError(13)

        assertEquals(SpeechEvent.Error(SpeechErrorKind.LANGUAGE_PACK_MISSING, 13), events.last())
        assertEquals(2, factory.created.size)
        assertEquals(2, factory.created.sumOf { it.starts })

        // A late error from the finished attempt must not start a third one.
        factory.created[1].callbacksOrFail().onError(13)
        assertEquals(2, factory.created.size)
    }

    @Test
    fun fallbackFailingWithNetworkErrorGivesNetworkError() {
        val factory = FakeRecognizerFactory(hasOnDevice = true)
        val source = sourceWith(factory)
        val events = source.collectEvents()

        source.start()
        factory.created[0].callbacksOrFail().onError(13)
        factory.created[1].callbacksOrFail().onError(2)

        assertEquals(SpeechEvent.Error(SpeechErrorKind.NETWORK, 2), events.last())
        assertEquals(2, factory.created.size)
    }

    @Test
    fun onDeviceNetworkErrorIsNotRetried() {
        val factory = FakeRecognizerFactory(hasOnDevice = true)
        val source = sourceWith(factory)
        val events = source.collectEvents()

        source.start()
        factory.created[0].callbacksOrFail().onError(2)

        assertEquals(1, factory.created.size)
        assertEquals(SpeechEvent.Error(SpeechErrorKind.NETWORK, 2), events.last())
    }

    @Test
    fun onDeviceNoMatchIsNotRetried() {
        val factory = FakeRecognizerFactory(hasOnDevice = true)
        val source = sourceWith(factory)
        val events = source.collectEvents()

        source.start()
        factory.created[0].callbacksOrFail().onError(7)

        assertEquals(1, factory.created.size)
        assertEquals(SpeechEvent.Error(SpeechErrorKind.NO_MATCH, 7), events.last())
    }

    @Test
    fun systemOnlyDeviceUsesDefaultRecognizerAndNeverFallsBack() {
        val factory = FakeRecognizerFactory(hasOnDevice = false)
        val source = sourceWith(factory)
        val events = source.collectEvents()

        source.start()
        factory.created[0].callbacksOrFail().onError(13)

        assertEquals(1, factory.created.size)
        assertFalse(factory.created[0].onDeviceEngine)
        assertEquals(SpeechEvent.Error(SpeechErrorKind.LANGUAGE_PACK_MISSING, 13), events.last())
    }

    @Test
    fun noRecognitionServiceRefusesStartWithoutCreatingARecognizer() {
        val factory = FakeRecognizerFactory(hasService = false)
        val source = sourceWith(factory)

        assertEquals(SpeechStartResult.UNAVAILABLE, source.start())
        assertTrue(factory.created.isEmpty())
    }

    @Test
    fun missingPermissionRefusesStartWithoutCreatingARecognizer() {
        val factory = FakeRecognizerFactory()
        val source = sourceWith(factory, permission = false)

        assertEquals(SpeechStartResult.NO_PERMISSION, source.start())
        assertTrue(factory.created.isEmpty())
    }

    @Test
    fun secondStartWhileListeningIsBusyAndCreatesNothing() {
        val factory = FakeRecognizerFactory()
        val source = sourceWith(factory)
        source.collectEvents()

        source.start()
        assertEquals(SpeechStartResult.BUSY, source.start())
        assertEquals(1, factory.created.size)
    }

    @Test
    fun staleCallbacksFromDestroyedAttemptAreIgnored() {
        val factory = FakeRecognizerFactory(hasOnDevice = true)
        val source = sourceWith(factory)
        val events = source.collectEvents()

        source.start()
        val onDeviceCallbacks = factory.created[0].callbacksOrFail()
        onDeviceCallbacks.onError(13)
        onDeviceCallbacks.onResults("سلام")
        onDeviceCallbacks.onPartial("سلا")

        assertFalse(events.any { it is SpeechEvent.Final || it is SpeechEvent.Partial })
    }

    @Test
    fun partialsFillTextAndBlankPartialsAreDropped() {
        val factory = FakeRecognizerFactory()
        val source = sourceWith(factory)
        val events = source.collectEvents()

        source.start()
        factory.created[0].callbacksOrFail().onPartial("  ")
        factory.created[0].callbacksOrFail().onPartial("۴ قوطی")

        assertEquals(
            listOf(SpeechEvent.EngineChanged(SpeechEngine.SYSTEM, null), SpeechEvent.Partial("۴ قوطی")),
            events,
        )
    }

    @Test
    fun blankResultIsNoMatch() {
        val factory = FakeRecognizerFactory()
        val source = sourceWith(factory)
        val events = source.collectEvents()

        source.start()
        factory.created[0].callbacksOrFail().onResults("   ")

        assertEquals(SpeechEvent.Error(SpeechErrorKind.NO_MATCH), events.last())
    }

    @Test
    fun stopAsksTheCurrentRecognizerToFinish() {
        val factory = FakeRecognizerFactory(hasOnDevice = true)
        val source = sourceWith(factory)
        source.collectEvents()

        source.start()
        source.stop()

        assertEquals(1, factory.created[0].stops)
    }

    @Test
    fun releaseDestroysEveryRecognizerCreated() {
        val factory = FakeRecognizerFactory(hasOnDevice = true)
        val source = sourceWith(factory)
        source.collectEvents()

        source.start()
        factory.created[0].callbacksOrFail().onError(13)
        source.release()

        assertTrue(factory.created.all { it.destroyed })
    }

    @Test
    fun recognizerIntentForOnDeviceHasNoPreferOffline() {
        val intent: Intent = AndroidSpeechSource.recognizerIntent(onDevice = true)
        assertFalse(intent.hasExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE))
    }

    @Test
    fun faPackStatusReadsPersianTagsOnly() {
        assertEquals(PackStatus.INSTALLED, faPackStatus(listOf("en-US", "fa-IR"), emptyList()))
        assertEquals(PackStatus.PENDING, faPackStatus(emptyList(), listOf("fa-Latn-IR")))
        assertEquals(PackStatus.NOT_INSTALLED, faPackStatus(listOf("en-US"), emptyList()))
    }
}

/** Scripted factory. Every [create] call records a [FakeRecognizer]; tests drive its callbacks. */
class FakeRecognizerFactory(
    private val hasService: Boolean = true,
    private val hasOnDevice: Boolean = false,
) : RecognizerPortFactory {
    val created = mutableListOf<FakeRecognizer>()

    override fun serviceAvailable(): Boolean = hasService

    override fun onDeviceAvailable(): Boolean = hasOnDevice

    override fun create(onDevice: Boolean, callbacks: RecognizerCallbacks): FakeRecognizer {
        val recognizer = FakeRecognizer(onDeviceEngine = onDevice, callbacks = callbacks)
        created += recognizer
        return recognizer
    }

    override fun createForPack(): RecognizerPort = FakeRecognizer(onDeviceEngine = hasOnDevice, callbacks = null)
}

class FakeRecognizer(
    val onDeviceEngine: Boolean,
    val callbacks: RecognizerCallbacks?,
) : RecognizerPort {
    var starts = 0
        private set
    var stops = 0
        private set
    var destroyed = false
        private set
    var lastIntent: Intent? = null
        private set

    override fun startListening(intent: Intent) {
        starts++
        lastIntent = intent
    }

    override fun stopListening() {
        stops++
    }

    override fun checkSupport(intent: Intent, onResult: (PackStatus) -> Unit) = onResult(PackStatus.UNKNOWN)

    override fun triggerDownload(intent: Intent, onEvent: (PackEvent) -> Unit) = onEvent(PackEvent.Failed(-1))

    override fun destroy() {
        destroyed = true
    }
}
