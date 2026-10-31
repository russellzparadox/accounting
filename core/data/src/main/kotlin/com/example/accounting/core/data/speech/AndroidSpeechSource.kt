package com.example.accounting.core.data.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * fa-IR recognizer with one fallback. Attempt 1 is on-device when the phone offers it (API 31+), otherwise
 * the default service. If the on-device attempt fails with a code in [FALLBACK_CODES], attempt 2 uses the
 * default service (network allowed). Never more than [MAX_ATTEMPTS] attempts per session.
 * Main thread only: recognizers are created, used and destroyed there.
 */
class AndroidSpeechSource(
    context: Context,
    private val factory: RecognizerPortFactory = AndroidRecognizerFactory(context),
    private val hasPermission: () -> Boolean = {
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    },
) : SpeechSource {
    private val mutableEvents = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = EVENT_BUFFER)
    override val events: Flow<SpeechEvent> = mutableEvents.asSharedFlow()

    private var port: RecognizerPort? = null
    private var listening = false
    private var attempts = 0

    /** Identifies the live attempt. Callbacks from an older (destroyed) attempt carry an old id and are ignored. */
    private var attemptId = 0

    override fun start(): SpeechStartResult {
        if (!hasPermission()) return SpeechStartResult.NO_PERMISSION
        if (!factory.serviceAvailable()) return SpeechStartResult.UNAVAILABLE
        if (listening) return SpeechStartResult.BUSY
        attempts = 0
        listening = true
        begin(if (factory.onDeviceAvailable()) SpeechEngine.ON_DEVICE else SpeechEngine.SYSTEM, afterErrorCode = null)
        return SpeechStartResult.STARTED
    }

    override fun stop() {
        if (listening) port?.stopListening()
    }

    override fun release() {
        attemptId++
        port?.destroy()
        port = null
        listening = false
    }

    /** One attempt: destroys the previous recognizer first, so at most one is alive. */
    private fun begin(engine: SpeechEngine, afterErrorCode: Int?) {
        attempts++
        port?.destroy()
        val id = ++attemptId
        val next = factory.create(engine == SpeechEngine.ON_DEVICE, callbacksFor(id, engine))
        port = next
        emit(SpeechEvent.EngineChanged(engine, afterErrorCode))
        next.startListening(recognizerIntent(onDevice = engine == SpeechEngine.ON_DEVICE))
    }

    private fun callbacksFor(id: Int, engine: SpeechEngine) = object : RecognizerCallbacks {
        override fun onPartial(text: String) {
            if (isCurrent(id) && text.isNotBlank()) emit(SpeechEvent.Partial(text))
        }

        override fun onResults(text: String?) {
            if (!isCurrent(id)) return
            listening = false
            emit(if (text.isNullOrBlank()) SpeechEvent.Error(SpeechErrorKind.NO_MATCH) else SpeechEvent.Final(text))
        }

        override fun onError(code: Int) {
            if (!isCurrent(id)) return
            if (engine == SpeechEngine.ON_DEVICE && attempts < MAX_ATTEMPTS && code in FALLBACK_CODES) {
                begin(SpeechEngine.SYSTEM, afterErrorCode = code)
                return
            }
            listening = false
            emit(SpeechEvent.Error(mapRecognizerError(code), code))
        }
    }

    private fun isCurrent(id: Int): Boolean = listening && id == attemptId

    private fun emit(event: SpeechEvent) {
        mutableEvents.tryEmit(event)
    }

    companion object {
        private const val EVENT_BUFFER = 16
        private const val LANGUAGE = "fa-IR"
        private const val MAX_RESULTS = 3
        const val MAX_ATTEMPTS = 2

        // Codes added after API 26. Literals keep the minSdk-26 lint check quiet.
        private const val ERROR_TOO_MANY_REQUESTS = 10
        private const val ERROR_SERVER_DISCONNECTED = 11
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13
        private const val ERROR_CLIENT = 5

        /** On-device codes that trigger the single fallback: client error, server disconnected, language missing. */
        val FALLBACK_CODES: Set<Int> = setOf(ERROR_CLIENT, ERROR_SERVER_DISCONNECTED, ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE)

        /** Recognizer intent: fa-IR, partial results, up to 3 alternatives. Offline preference only for the default recognizer. */
        fun recognizerIntent(onDevice: Boolean): Intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
                if (!onDevice) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

        /** SpeechRecognizer error code to [SpeechErrorKind]. Language codes 12/13 mean the fa-IR pack is missing. */
        fun mapRecognizerError(code: Int): SpeechErrorKind = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechErrorKind.NO_MATCH
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, ERROR_TOO_MANY_REQUESTS -> SpeechErrorKind.BUSY
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechErrorKind.NO_PERMISSION
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER, ERROR_SERVER_DISCONNECTED -> SpeechErrorKind.NETWORK
            ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> SpeechErrorKind.LANGUAGE_PACK_MISSING
            else -> SpeechErrorKind.OTHER
        }
    }
}
