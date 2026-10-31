package com.example.accounting.core.data.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * On-device recognizer (fa-IR). Prefers [SpeechRecognizer.createOnDeviceSpeechRecognizer] on API 31+
 * when it is available; otherwise the default recognizer with EXTRA_PREFER_OFFLINE.
 * Main thread only: the recognizer is created, used and destroyed there.
 */
class AndroidSpeechSource(private val context: Context) : SpeechSource {
    private val mutableEvents = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = EVENT_BUFFER)
    override val events: Flow<SpeechEvent> = mutableEvents.asSharedFlow()

    private var recognizer: SpeechRecognizer? = null
    private var onDevice = false
    private var listening = false

    override fun start(): SpeechStartResult {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return SpeechStartResult.NO_PERMISSION
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return SpeechStartResult.UNAVAILABLE
        if (listening) return SpeechStartResult.BUSY
        val rec = recognizer ?: createRecognizer().also { recognizer = it }
        rec.setRecognitionListener(listener)
        rec.startListening(recognizerIntent(onDevice))
        listening = true
        return SpeechStartResult.STARTED
    }

    override fun stop() {
        if (listening) recognizer?.stopListening()
    }

    override fun release() {
        recognizer?.destroy()
        recognizer = null
        listening = false
    }

    private fun createRecognizer(): SpeechRecognizer {
        // The SDK check sits in the same condition so lint sees the API 31 calls as guarded.
        if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onDevice = true
            return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }
        onDevice = false
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun emit(event: SpeechEvent) {
        mutableEvents.tryEmit(event)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            listening = false
            emit(SpeechEvent.Error(mapRecognizerError(error)))
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val text = firstResult(results)
            emit(if (text.isEmpty()) SpeechEvent.Error(SpeechErrorKind.NO_MATCH) else SpeechEvent.Final(text))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = firstResult(partialResults)
            if (text.isNotEmpty()) emit(SpeechEvent.Partial(text))
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    companion object {
        private const val EVENT_BUFFER = 16
        private const val LANGUAGE = "fa-IR"
        private const val MAX_RESULTS = 3

        // Codes added after API 26. Literals keep the minSdk-26 lint check quiet.
        private const val ERROR_TOO_MANY_REQUESTS = 10
        private const val ERROR_SERVER_DISCONNECTED = 11
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13

        /** Recognizer intent: fa-IR, partial results, up to 3 alternatives. Offline preference only for the default recognizer. */
        fun recognizerIntent(onDevice: Boolean): Intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
                if (!onDevice) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

        /** SpeechRecognizer error code to [SpeechErrorKind]. */
        fun mapRecognizerError(code: Int): SpeechErrorKind = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechErrorKind.NO_MATCH
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, ERROR_TOO_MANY_REQUESTS -> SpeechErrorKind.BUSY
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechErrorKind.NO_PERMISSION
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER, ERROR_SERVER_DISCONNECTED -> SpeechErrorKind.NETWORK
            ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> SpeechErrorKind.UNAVAILABLE
            else -> SpeechErrorKind.OTHER
        }

        private fun firstResult(bundle: Bundle?): String =
            bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
    }
}
