package com.example.accounting.core.data.speech

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Engine routing: the setting picks the engine, AUTO falls back once to the cloud, inactive events are dropped. */
class RoutingSpeechSourceTest {
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val local = ScriptedSpeech()
    private val cloud = ScriptedSpeech()
    private val prefs = FakePrefs()
    private val seen = mutableListOf<SpeechEvent>()
    private lateinit var routing: RoutingSpeechSource

    @Before
    fun setUp() {
        routing = RoutingSpeechSource(local, cloud, prefs, scope)
        scope.launch { routing.events.toList(seen) }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun deviceOnlyNeverTouchesTheCloud() {
        prefs.engine = VoiceEngine.DEVICE_ONLY
        assertEquals(SpeechStartResult.STARTED, routing.start())
        assertEquals(1, local.starts)
        assertEquals(0, cloud.starts)
    }

    @Test
    fun cloudOnlyStartsTheCloudAndLabelsIt() {
        prefs.engine = VoiceEngine.CLOUD_ONLY
        assertEquals(SpeechStartResult.STARTED, routing.start())
        assertEquals(0, local.starts)
        assertEquals(1, cloud.starts)
        assertEquals(listOf<SpeechEvent>(SpeechEvent.EngineChanged(SpeechEngine.CLOUD)), seen)
    }

    @Test
    fun cloudOnlyWithoutConfigReportsNotConfigured() {
        prefs.engine = VoiceEngine.CLOUD_ONLY
        cloud.startResult = SpeechStartResult.NOT_CONFIGURED
        assertEquals(SpeechStartResult.NOT_CONFIGURED, routing.start())
        assertTrue(seen.isEmpty())
    }

    @Test
    fun autoFallsBackToTheCloudWhenNoRecognizerIsInstalled() {
        local.startResult = SpeechStartResult.UNAVAILABLE
        assertEquals(SpeechStartResult.STARTED, routing.start())
        assertEquals(1, cloud.starts)
        assertEquals(listOf<SpeechEvent>(SpeechEvent.EngineChanged(SpeechEngine.CLOUD)), seen)
    }

    @Test
    fun autoWithoutConsentDoesNotUseTheCloud() {
        prefs.cloudConsented = false
        local.startResult = SpeechStartResult.UNAVAILABLE
        assertEquals(SpeechStartResult.UNAVAILABLE, routing.start())
        assertEquals(0, cloud.starts)
    }

    @Test
    fun autoWithoutBackendDoesNotUseTheCloud() {
        prefs.cloudConfigured = false
        local.startResult = SpeechStartResult.UNAVAILABLE
        assertEquals(SpeechStartResult.UNAVAILABLE, routing.start())
        assertEquals(0, cloud.starts)
    }

    @Test
    fun autoFallsBackOnceWhenOnDeviceNetworkFails_andHidesTheLocalError() {
        routing.start()
        local.emit(SpeechEvent.Error(SpeechErrorKind.NETWORK, 2))

        assertEquals(1, cloud.starts)
        assertEquals(listOf<SpeechEvent>(SpeechEvent.EngineChanged(SpeechEngine.CLOUD)), seen)
    }

    @Test
    fun cloudResultIsForwardedAfterFallback() {
        routing.start()
        local.emit(SpeechEvent.Error(SpeechErrorKind.LANGUAGE_PACK_MISSING, 13))
        cloud.emit(SpeechEvent.Processing)
        cloud.emit(SpeechEvent.Final("نوشابه"))

        assertEquals(SpeechEvent.Final("نوشابه"), seen.last())
        assertTrue(seen.contains(SpeechEvent.Processing))
    }

    @Test
    fun autoFallbackFailureIsReportedWhenTheCloudCannotStart() {
        routing.start()
        cloud.startResult = SpeechStartResult.BUSY
        local.emit(SpeechEvent.Error(SpeechErrorKind.NETWORK, 2))

        assertEquals(listOf<SpeechEvent>(SpeechEvent.Error(SpeechErrorKind.NETWORK, 2)), seen)
    }

    @Test
    fun otherLocalErrorsAreShownAndNeverFallBack() {
        routing.start()
        local.emit(SpeechEvent.Error(SpeechErrorKind.NO_MATCH, 7))
        assertEquals(0, cloud.starts)
        assertEquals(listOf<SpeechEvent>(SpeechEvent.Error(SpeechErrorKind.NO_MATCH, 7)), seen)
    }

    @Test
    fun networkErrorWithoutConsentIsShownAsItIs() {
        prefs.cloudConsented = false
        routing.start()
        local.emit(SpeechEvent.Error(SpeechErrorKind.NETWORK, 2))
        assertEquals(0, cloud.starts)
        assertEquals(listOf<SpeechEvent>(SpeechEvent.Error(SpeechErrorKind.NETWORK, 2)), seen)
    }

    @Test
    fun deviceOnlyNeverFallsBackEvenOnNetworkError() {
        prefs.engine = VoiceEngine.DEVICE_ONLY
        routing.start()
        local.emit(SpeechEvent.Error(SpeechErrorKind.NETWORK, 2))
        assertEquals(0, cloud.starts)
        assertEquals(listOf<SpeechEvent>(SpeechEvent.Error(SpeechErrorKind.NETWORK, 2)), seen)
    }

    @Test
    fun eventsFromTheInactiveEngineAreDropped() {
        prefs.engine = VoiceEngine.DEVICE_ONLY
        routing.start()
        cloud.emit(SpeechEvent.Final("ghost"))
        local.emit(SpeechEvent.Final("ok"))
        assertEquals(listOf<SpeechEvent>(SpeechEvent.Final("ok")), seen)
    }

    @Test
    fun stopGoesToTheActiveEngineAndReleaseReleasesBoth() {
        prefs.engine = VoiceEngine.CLOUD_ONLY
        routing.start()
        routing.stop()
        assertEquals(1, cloud.stops)
        assertEquals(0, local.stops)

        routing.release()
        assertTrue(local.released && cloud.released)
    }
}
