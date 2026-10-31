package com.example.accounting.core.data.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Last voice error of this process: kind, platform code (null when none), engine of the failing attempt. */
data class VoiceErrorRecord(val kind: SpeechErrorKind, val code: Int?, val engine: SpeechEngine?)

/**
 * Process-wide record of the last voice error and engine, for the Settings diagnostics card. In memory only:
 * it resets when the app process restarts. One instance is bound as a singleton.
 */
class SpeechDiagnostics {
    private val mutableLastError = MutableStateFlow<VoiceErrorRecord?>(null)
    val lastError: StateFlow<VoiceErrorRecord?> = mutableLastError.asStateFlow()

    private var engine: SpeechEngine? = null

    fun recordEngine(engine: SpeechEngine) {
        this.engine = engine
    }

    fun recordError(kind: SpeechErrorKind, code: Int?) {
        mutableLastError.value = VoiceErrorRecord(kind, code, engine)
    }
}

/** Facts for the Settings diagnostics card. No device model, no account data. */
data class SpeechReport(
    val apiLevel: Int,
    val recognitionAvailable: Boolean,
    val onDeviceAvailable: Boolean,
    /** Package names of installed recognition services, sorted. */
    val servicePackages: List<String>,
    /** fa-IR pack state. Null = not checked (API below 33, or no service). */
    val faPack: PackStatus?,
    val lastError: VoiceErrorRecord?,
)

/** Collects a [SpeechReport]. The fa-IR check is asynchronous, so the report arrives through a callback. */
class SpeechProbe(
    private val context: Context,
    private val factory: RecognizerPortFactory,
    private val diagnostics: SpeechDiagnostics,
    private val pack: SpeechLanguagePack,
) {
    fun snapshot(onReport: (SpeechReport) -> Unit) {
        val servicePackages = installedServicePackages()
        val available = factory.serviceAvailable()
        fun report(faPack: PackStatus?) = SpeechReport(
            apiLevel = Build.VERSION.SDK_INT,
            recognitionAvailable = available,
            onDeviceAvailable = factory.onDeviceAvailable(),
            servicePackages = servicePackages,
            faPack = faPack,
            lastError = diagnostics.lastError.value,
        )
        if (!available) {
            onReport(report(null))
            return
        }
        pack.check { status -> onReport(report(status)) }
    }

    private fun installedServicePackages(): List<String> =
        context.packageManager
            .queryIntentServices(Intent(RECOGNITION_SERVICE_ACTION), 0)
            .mapNotNull { it.serviceInfo?.packageName }
            .distinct()
            .sorted()

    companion object {
        const val RECOGNITION_SERVICE_ACTION = "android.speech.RecognitionService"
    }
}
