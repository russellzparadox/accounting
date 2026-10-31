package com.example.accounting.core.data.speech

import kotlinx.coroutines.flow.Flow

/**
 * Failure classes of a speech session. The UI maps each one to Persian text.
 * NO_RECOGNIZER_INSTALLED: no RecognitionService on the phone at all.
 * LANGUAGE_PACK_MISSING: the recognizer refused fa-IR (offline pack not installed).
 */
enum class SpeechErrorKind {
    NO_MATCH,
    NO_RECOGNIZER_INSTALLED,
    LANGUAGE_PACK_MISSING,
    BUSY,
    NO_PERMISSION,
    NETWORK,
    UNSUPPORTED,
    OTHER,
    /** Cloud: server unreachable, timed out, or a 5xx. */
    CLOUD_UNAVAILABLE,
    /** Cloud: the proxy token was refused (401/403). */
    CLOUD_UNAUTHORIZED,
    /** Cloud: the server or its speech provider is rate limiting. */
    CLOUD_RATE_LIMITED,
    /** Cloud: the recording exceeded the server's upload limit. */
    CLOUD_TOO_LARGE,
    /** Cloud: no backend URL or token saved, or the server has STT disabled (501). */
    CLOUD_NOT_CONFIGURED,
}

/**
 * Which engine the current attempt uses. ON_DEVICE = API 31+ on-device model; SYSTEM = default service;
 * CLOUD = recording uploaded to the backend (/v1/audio/transcriptions).
 */
enum class SpeechEngine { ON_DEVICE, SYSTEM, CLOUD }

/** Events of one session. Callers never submit on their own: a [Final] only fills the input. */
sealed interface SpeechEvent {
    /** Live hypothesis. Replaces the previous partial of the same session. */
    data class Partial(val text: String) : SpeechEvent

    /** Final text of the session (first alternative). */
    data class Final(val text: String) : SpeechEvent

    /** Recording finished and the text is being transcribed (cloud). Not text: the input field is left alone. */
    data object Processing : SpeechEvent

    /** Session ended with an error. [code] is the platform SpeechRecognizer code, null when not from the recognizer. */
    data class Error(val kind: SpeechErrorKind, val code: Int? = null) : SpeechEvent

    /**
     * A new attempt starts on [engine]. [afterErrorCode] is set when this is the fallback after the
     * on-device attempt failed with that code. For diagnostics only.
     */
    data class EngineChanged(val engine: SpeechEngine, val afterErrorCode: Int? = null) : SpeechEvent
}

/** Immediate result of [SpeechSource.start], before any event arrives. UNAVAILABLE = no recognition service. */
enum class SpeechStartResult { STARTED, BUSY, UNAVAILABLE, NO_PERMISSION, UNSUPPORTED, NOT_CONFIGURED }

/**
 * Speech-to-text behind an interface. On-device with a single system fallback ([AndroidSpeechSource]),
 * cloud upload ([CloudSpeechSource]), and the engine routing between them ([RoutingSpeechSource]). [start], [stop] and [release] are called on the main thread. Only one session at a time.
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
