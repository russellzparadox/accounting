package com.example.accounting.core.data.speech

/** fa-IR language-pack operations, behind an interface so the ViewModel can be tested. Main thread only. */
interface SpeechLanguagePack {
    /** Reports the fa-IR pack state. */
    fun check(onResult: (PackStatus) -> Unit)

    /** Starts the download and reports progress and the result through [onEvent]. */
    fun download(onEvent: (PackEvent) -> Unit)

    fun release()
}

/**
 * fa-IR language-pack check and download (API 33+). Main thread only.
 * A recognizer is held while a download runs and destroyed on Done, Failed or [release]; the platform may
 * cancel the download if the recognizer goes away.
 */
class LanguagePackController(private val factory: RecognizerPortFactory) : SpeechLanguagePack {
    private var downloadPort: RecognizerPort? = null

    /** Reports whether fa-IR is installed. Creates and destroys a short-lived recognizer. */
    override fun check(onResult: (PackStatus) -> Unit) {
        val port = factory.createForPack()
        port.checkSupport(AndroidSpeechSource.recognizerIntent(onDevice = true)) { status ->
            port.destroy()
            onResult(status)
        }
    }

    /** Starts the download. [onEvent] gets progress and the result. The recognizer lives until a terminal event. */
    override fun download(onEvent: (PackEvent) -> Unit) {
        downloadPort?.destroy()
        val port = factory.createForPack()
        downloadPort = port
        port.triggerDownload(AndroidSpeechSource.recognizerIntent(onDevice = true)) { event ->
            if (event is PackEvent.Done || event is PackEvent.Failed) {
                port.destroy()
                if (downloadPort === port) downloadPort = null
            }
            onEvent(event)
        }
    }

    override fun release() {
        downloadPort?.destroy()
        downloadPort = null
    }
}
