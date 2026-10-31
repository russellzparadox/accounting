package com.example.accounting.core.data.speech

import kotlinx.coroutines.flow.Flow

/** Failure classes of a speech session. The UI maps each one to Persian text. */
enum class SpeechErrorKind { NO_MATCH, UNAVAILABLE, BUSY, NO_PERMISSION, NETWORK, UNSUPPORTED, OTHER }

/** Events of one session. Callers never submit on their own: a [Final] only fills the input. */
sealed interface SpeechEvent {
    /** Live hypothesis. Replaces the previous partial of the same session. */
    data class Partial(val text: String) : SpeechEvent

    /** Final text of the session (first alternative). */
    data class Final(val text: String) : SpeechEvent

    data class Error(val kind: SpeechErrorKind) : SpeechEvent
}

/** Immediate result of [SpeechSource.start], before any event arrives. */
enum class SpeechStartResult { STARTED, BUSY, UNAVAILABLE, NO_PERMISSION, UNSUPPORTED }

/**
 * Speech-to-text behind an interface. v1 impl is on-device ([AndroidSpeechSource]); cloud is a stub.
 * [start], [stop] and [release] are called on the main thread. Only one session at a time.
 */
interface SpeechSource {
    /** Events of the session in progress. Never completes. Collect before calling [start]. */
    val events: Flow<SpeechEvent>

    /** Begins one fa-IR session with partial results on. Returns at once; outcome arrives on [events]. */
    fun start(): SpeechStartResult

    /** Asks the recognizer to finish. The final text or an error follows on [events]. */
    fun stop()

    /** Destroys the recognizer. Call from the owner's onCleared. */
    fun release()
}
