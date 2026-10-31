package com.example.accounting.feature.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accounting.core.data.speech.PackEvent
import com.example.accounting.core.data.speech.PackStatus
import com.example.accounting.core.data.speech.SpeechDiagnostics
import com.example.accounting.core.data.speech.SpeechEngine
import com.example.accounting.core.data.speech.SpeechErrorKind
import com.example.accounting.core.data.speech.SpeechEvent
import com.example.accounting.core.data.speech.SpeechLanguagePack
import com.example.accounting.core.data.speech.SpeechSource
import com.example.accounting.core.data.speech.SpeechStartResult
import com.example.accounting.core.data.speech.VoiceEngine
import com.example.accounting.core.data.speech.VoicePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Commands from the mic button and the lifecycle. */
sealed interface VoiceAction {
    /** [currentText] is the input text when listening starts. Recognized speech is appended to it. */
    data class Start(val currentText: String) : VoiceAction

    /** Asks the recognizer to finish. Listening ends when its final result or error arrives. */
    data object Stop : VoiceAction

    /** Starts the fa-IR language-pack download (API 33+). */
    data object DownloadLanguagePack : VoiceAction

    /** The user accepted the cloud consent dialog: remember it and start the session that asked. */
    data object ConfirmCloudConsent : VoiceAction

    /** The user declined the cloud consent dialog: nothing starts. */
    data object DismissCloudConsent : VoiceAction
}

/**
 * [transcript] is the text the input field should show. A new instance per change, so the UI can
 * key on it even when two results have the same words.
 */
data class VoiceTranscript(val text: String, val seq: Int)

/** Language-pack line under the voice error. Only shown for the pack error and while a download runs. */
sealed interface PackUi {
    data object None : PackUi

    /** fa-IR not installed: the download button is offered. */
    data object Offer : PackUi

    /** The system has the download queued or is installing it. */
    data object Pending : PackUi

    data class Downloading(val percent: Int?) : PackUi

    /** API 33: download triggered, no progress available. */
    data object Started : PackUi

    data object Done : PackUi

    data class Failed(val code: Int) : PackUi
}

data class VoiceUiState(
    val listening: Boolean = false,
    val error: SpeechErrorKind? = null,
    /** Platform code of [error] for the small muted «(کد …)» tail. Null when the error has no code. */
    val errorCode: Int? = null,
    val transcript: VoiceTranscript? = null,
    val pack: PackUi = PackUi.None,
    /** Engine of the current or last session. Shown as a small label while listening. */
    val engine: SpeechEngine? = null,
    /** Cloud audio is about to be used for the first time: the consent dialog is open. */
    val consentRequired: Boolean = false,
    /** Recording is being transcribed (cloud). Mic is off, the field is left alone. */
    val processing: Boolean = false,
)

/**
 * Voice state layer. Owns one [SpeechSource] session at a time. Never submits: partial and final text
 * only fill the input, and the user sends it with the usual send action.
 */
@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val speech: SpeechSource,
    private val diagnostics: SpeechDiagnostics,
    private val languagePack: SpeechLanguagePack,
    private val prefs: VoicePreferences,
) : ViewModel() {

    private val mutableState = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = mutableState.asStateFlow()

    /** Text already in the input when the current session started. */
    private var base = ""
    private var seq = 0

    /** Text in the input when the mic was tapped, while the consent dialog is open. */
    private var pendingText: String? = null

    init {
        viewModelScope.launch { speech.events.collect { handle(it) } }
    }

    fun onAction(action: VoiceAction) {
        when (action) {
            is VoiceAction.Start -> start(action.currentText)
            VoiceAction.Stop -> speech.stop()
            VoiceAction.DownloadLanguagePack -> downloadPack()
            VoiceAction.ConfirmCloudConsent -> confirmConsent()
            VoiceAction.DismissCloudConsent -> {
                pendingText = null
                mutableState.update { it.copy(consentRequired = false) }
            }
        }
    }

    private fun start(currentText: String) {
        val current = mutableState.value
        if (current.listening || current.processing || current.consentRequired) return
        if (needsCloudConsent()) {
            pendingText = currentText
            mutableState.update { it.copy(consentRequired = true) }
            return
        }
        startNow(currentText)
    }

    /** Cloud audio may leave the phone: needs a saved backend and the engine setting allowing it, and no earlier consent. */
    private fun needsCloudConsent(): Boolean =
        prefs.engine != VoiceEngine.DEVICE_ONLY && prefs.cloudConfigured && !prefs.cloudConsented

    private fun confirmConsent() {
        val text = pendingText ?: return
        pendingText = null
        prefs.acceptCloudConsent()
        mutableState.update { it.copy(consentRequired = false) }
        startNow(text)
    }

    private fun startNow(currentText: String) {
        base = currentText
        // Reset before speech.start(): the engine may report EngineChanged synchronously inside it.
        mutableState.update { it.copy(engine = null, processing = false) }
        val error = when (speech.start()) {
            SpeechStartResult.STARTED -> null
            SpeechStartResult.BUSY -> SpeechErrorKind.BUSY
            SpeechStartResult.UNAVAILABLE -> SpeechErrorKind.NO_RECOGNIZER_INSTALLED
            SpeechStartResult.NO_PERMISSION -> SpeechErrorKind.NO_PERMISSION
            SpeechStartResult.UNSUPPORTED -> SpeechErrorKind.UNSUPPORTED
            SpeechStartResult.NOT_CONFIGURED -> SpeechErrorKind.CLOUD_NOT_CONFIGURED
        }
        if (error != null) diagnostics.recordError(error, null)
        mutableState.update {
            it.copy(listening = error == null, error = error, errorCode = null, pack = PackUi.None)
        }
    }

    private fun handle(event: SpeechEvent) {
        when (event) {
            is SpeechEvent.Partial -> showText(event.text)
            is SpeechEvent.Final -> {
                showText(event.text)
                mutableState.update {
                    it.copy(listening = false, error = null, errorCode = null, processing = false)
                }
            }
            SpeechEvent.Processing -> mutableState.update {
                it.copy(listening = false, error = null, errorCode = null, processing = true)
            }
            is SpeechEvent.Error -> failed(event.kind, event.code)
            is SpeechEvent.EngineChanged -> {
                diagnostics.recordEngine(event.engine)
                mutableState.update { it.copy(engine = event.engine) }
            }
        }
    }

    private fun failed(kind: SpeechErrorKind, code: Int?) {
        diagnostics.recordError(kind, code)
        mutableState.update {
            it.copy(listening = false, error = kind, errorCode = code, pack = PackUi.None, processing = false)
        }
        if (kind == SpeechErrorKind.LANGUAGE_PACK_MISSING) checkPack()
    }

    /** Decides whether to offer the download. Ignored if the user has started another session meanwhile. */
    private fun checkPack() {
        languagePack.check { status ->
            val pack = when (status) {
                PackStatus.NOT_INSTALLED -> PackUi.Offer
                PackStatus.PENDING -> PackUi.Pending
                PackStatus.INSTALLED, PackStatus.UNSUPPORTED_API, PackStatus.UNKNOWN -> PackUi.None
            }
            mutableState.update {
                if (it.error == SpeechErrorKind.LANGUAGE_PACK_MISSING) it.copy(pack = pack) else it
            }
        }
    }

    private fun downloadPack() {
        if (mutableState.value.pack is PackUi.Downloading) return
        mutableState.update { it.copy(pack = PackUi.Downloading(null)) }
        languagePack.download { event ->
            val next = when (event) {
                PackEvent.Started -> PackUi.Started
                PackEvent.Scheduled -> PackUi.Pending
                is PackEvent.Progress -> PackUi.Downloading(event.percent)
                PackEvent.Done -> PackUi.Done
                is PackEvent.Failed -> PackUi.Failed(event.code)
            }
            mutableState.update { it.copy(pack = next) }
        }
    }

    /** Keeps the text the user had before speaking; recognized speech follows it after one space. */
    private fun showText(recognized: String) {
        val text = combineClarification(base, recognized)
        mutableState.update { it.copy(transcript = VoiceTranscript(text, ++seq)) }
    }

    override fun onCleared() {
        speech.release()
        languagePack.release()
    }
}
