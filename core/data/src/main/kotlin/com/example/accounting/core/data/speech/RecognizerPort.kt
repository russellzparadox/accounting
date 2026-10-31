package com.example.accounting.core.data.speech

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.ModelDownloadListener
import android.speech.RecognitionSupportCallback
import android.speech.SpeechRecognizer
import java.util.concurrent.Executor

/** Callbacks of one recognizer attempt. Called on the main thread. */
interface RecognizerCallbacks {
    fun onPartial(text: String)

    /** Null or blank text means no match. */
    fun onResults(text: String?)

    fun onError(code: Int)
}

/** One platform recognizer. Main thread only. Abstracted so the fallback chain can be tested without a device. */
interface RecognizerPort {
    fun startListening(intent: Intent)

    fun stopListening()

    /** Asks the recognizer whether fa-IR is installed (API 33+). Other APIs report [PackStatus.UNSUPPORTED_API]. */
    fun checkSupport(intent: Intent, onResult: (PackStatus) -> Unit)

    /** Starts the fa-IR download (API 33+). Progress comes only on API 34+. */
    fun triggerDownload(intent: Intent, onEvent: (PackEvent) -> Unit)

    fun destroy()
}

/** Creates recognizers and answers the availability questions. */
interface RecognizerPortFactory {
    /** A recognition service is installed and visible to this app. */
    fun serviceAvailable(): Boolean

    /** API 31+ and the on-device fa model path is offered by the phone. */
    fun onDeviceAvailable(): Boolean

    /** A recognizer that delivers results to [callbacks]. [onDevice] is honoured only when [onDeviceAvailable]. */
    fun create(onDevice: Boolean, callbacks: RecognizerCallbacks): RecognizerPort

    /** A recognizer without listener, for the language-pack check and download. Caller destroys it. */
    fun createForPack(): RecognizerPort
}

/** Language-pack state for fa-IR. */
enum class PackStatus { INSTALLED, NOT_INSTALLED, PENDING, UNSUPPORTED_API, UNKNOWN }

/** Progress of a language-pack download. Percent only on API 34+. */
sealed interface PackEvent {
    data object Started : PackEvent

    data object Scheduled : PackEvent

    data class Progress(val percent: Int) : PackEvent

    data object Done : PackEvent

    data class Failed(val code: Int) : PackEvent
}

/** Pure rule: fa-IR installed, queued, or missing. Language tags such as fa-IR or fa-Latn-IR both count as Persian. */
fun faPackStatus(installed: Collection<String>, pending: Collection<String>): PackStatus = when {
    installed.any { isPersianTag(it) } -> PackStatus.INSTALLED
    pending.any { isPersianTag(it) } -> PackStatus.PENDING
    else -> PackStatus.NOT_INSTALLED
}

internal fun isPersianTag(tag: String): Boolean = tag.startsWith("fa", ignoreCase = true)

/** Main-thread executor for platform callbacks. Context.getMainExecutor is API 28; minSdk is 26. */
private val mainThread = Executor { command -> Handler(Looper.getMainLooper()).post(command) }

/** Production factory over [SpeechRecognizer]. Every call happens on the main thread. */
class AndroidRecognizerFactory(private val context: Context) : RecognizerPortFactory {

    override fun serviceAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun onDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override fun create(onDevice: Boolean, callbacks: RecognizerCallbacks): RecognizerPort {
        val rec = if (onDevice && Build.VERSION.SDK_INT >= 31) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        rec.setRecognitionListener(listener(callbacks))
        return AndroidRecognizerPort(rec, mainThread)
    }

    override fun createForPack(): RecognizerPort {
        val rec = if (onDeviceAvailable()) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        return AndroidRecognizerPort(rec, mainThread)
    }

    private fun listener(callbacks: RecognizerCallbacks) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) = callbacks.onError(error)

        override fun onResults(results: Bundle?) = callbacks.onResults(firstResult(results))

        override fun onPartialResults(partialResults: Bundle?) {
            val text = firstResult(partialResults)
            if (!text.isNullOrBlank()) callbacks.onPartial(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
}

private class AndroidRecognizerPort(
    private val rec: SpeechRecognizer,
    private val mainExecutor: Executor,
) : RecognizerPort {

    override fun startListening(intent: Intent) = rec.startListening(intent)

    override fun stopListening() = rec.stopListening()

    override fun destroy() = rec.destroy()

    override fun checkSupport(intent: Intent, onResult: (PackStatus) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) {
            onResult(PackStatus.UNSUPPORTED_API)
            return
        }
        // Lint: API 33 calls sit inside the SDK_INT guard above.
        if (Build.VERSION.SDK_INT >= 33) {
            rec.checkRecognitionSupport(intent, mainExecutor, object : RecognitionSupportCallback {
                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                    onResult(
                        faPackStatus(
                            installed = recognitionSupport.installedOnDeviceLanguages,
                            pending = recognitionSupport.pendingOnDeviceLanguages,
                        ),
                    )
                }

                override fun onError(error: Int) = onResult(PackStatus.UNKNOWN)
            })
        }
    }

    override fun triggerDownload(intent: Intent, onEvent: (PackEvent) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) {
            onEvent(PackEvent.Failed(-1))
            return
        }
        if (Build.VERSION.SDK_INT >= 34) {
            rec.triggerModelDownload(intent, mainExecutor, object : ModelDownloadListener {
                override fun onProgress(completedPercent: Int) = onEvent(PackEvent.Progress(completedPercent))

                override fun onScheduled() = onEvent(PackEvent.Scheduled)

                override fun onSuccess() = onEvent(PackEvent.Done)

                override fun onError(error: Int) = onEvent(PackEvent.Failed(error))
            })
        } else if (Build.VERSION.SDK_INT >= 33) {
            // API 33: no listener. The caller tells the user to try again later.
            rec.triggerModelDownload(intent)
            onEvent(PackEvent.Started)
        }
    }
}
