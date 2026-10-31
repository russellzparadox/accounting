package com.example.accounting.feature.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accounting.core.data.speech.SpeechErrorKind
import com.example.accounting.core.data.speech.SpeechEvent
import com.example.accounting.core.data.speech.SpeechSource
import com.example.accounting.core.data.speech.SpeechStartResult
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
}

/**
 * [transcript] is the text the input field should show. A new instance per change, so the UI can
 * key on it even when two results have the same words.
 */
data class VoiceTranscript(val text: String, val seq: Int)

data class VoiceUiState(
    val listening: Boolean = false,
    val error: SpeechErrorKind? = null,
    val transcript: VoiceTranscript? = null,
)

/**
 * Voice state layer. Owns one [SpeechSource] session at a time. Never submits: partial and final text
 * only fill the input, and the user sends it with the usual send action.
 */
@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val speech: SpeechSource,
) : ViewModel() {

    private val mutableState = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = mutableState.asStateFlow()

    /** Text already in the input when the current session started. */
    private var base = ""
    private var seq = 0

    init {
        viewModelScope.launch { speech.events.collect { handle(it) } }
    }

    fun onAction(action: VoiceAction) {
        when (action) {
            is VoiceAction.Start -> start(action.currentText)
            VoiceAction.Stop -> speech.stop()
        }
    }

    private fun start(currentText: String) {
        if (mutableState.value.listening) return
        base = currentText
        val error = when (speech.start()) {
            SpeechStartResult.STARTED -> null
            SpeechStartResult.BUSY -> SpeechErrorKind.BUSY
            SpeechStartResult.UNAVAILABLE -> SpeechErrorKind.UNAVAILABLE
            SpeechStartResult.NO_PERMISSION -> SpeechErrorKind.NO_PERMISSION
            SpeechStartResult.UNSUPPORTED -> SpeechErrorKind.UNSUPPORTED
        }
        mutableState.update { it.copy(listening = error == null, error = error) }
    }

    private fun handle(event: SpeechEvent) {
        when (event) {
            is SpeechEvent.Partial -> showText(event.text)
            is SpeechEvent.Final -> {
                showText(event.text)
                mutableState.update { it.copy(listening = false, error = null) }
            }
            is SpeechEvent.Error -> mutableState.update { it.copy(listening = false, error = event.kind) }
        }
    }

    /** Keeps the text the user had before speaking; recognized speech follows it after one space. */
    private fun showText(recognized: String) {
        val text = combineClarification(base, recognized)
        mutableState.update { it.copy(transcript = VoiceTranscript(text, ++seq)) }
    }

    override fun onCleared() {
        speech.release()
    }
}
