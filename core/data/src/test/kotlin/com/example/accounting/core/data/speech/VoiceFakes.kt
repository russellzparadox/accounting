package com.example.accounting.core.data.speech

import com.example.accounting.core.data.config.BackendConfig
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Scripted [SpeechSource]: [startResult] is returned by [start]; tests push events with [emit]. */
class ScriptedSpeech(var startResult: SpeechStartResult = SpeechStartResult.STARTED) : SpeechSource {
    private val flow = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 32)
    override val events: Flow<SpeechEvent> = flow
    var starts = 0
        private set
    var stops = 0
        private set
    var released = false
        private set

    override fun start(): SpeechStartResult {
        starts++
        return startResult
    }

    override fun stop() {
        stops++
    }

    override fun release() {
        released = true
    }

    fun emit(event: SpeechEvent) {
        check(flow.tryEmit(event)) { "event dropped" }
    }
}

/** Preferences with settable values. */
class FakePrefs(
    override var engine: VoiceEngine = VoiceEngine.AUTO,
    override var cloudConfigured: Boolean = true,
    override var cloudConsented: Boolean = true,
) : VoicePreferences {
    override fun acceptCloudConsent() {
        cloudConsented = true
    }
}

/** Writes a small file on start, like a recorder would, and reports what it was asked to do. */
class FakeRecorder(var startOk: Boolean = true, var stopOk: Boolean = true) : AudioRecorder {
    var released = 0
        private set
    private var current: File? = null

    override fun start(file: File): Boolean {
        if (!startOk) return false
        file.writeBytes(AUDIO)
        current = file
        return true
    }

    override fun stop(): Boolean = stopOk && current != null

    override fun release() {
        released++
        current = null
    }

    companion object {
        val AUDIO = "RECORDED-AUDIO".toByteArray()
    }
}

/** Returns [result]; records the file bytes it was given and whether the file existed at call time. */
class FakeTranscriber(var result: TranscriptResult = TranscriptResult.Text("نوشابه")) : CloudTranscriber {
    val calls = mutableListOf<Pair<ByteArray?, BackendConfig>>()
    var fileExisted = false
        private set

    override fun transcribe(audio: File, config: BackendConfig): TranscriptResult {
        fileExisted = audio.exists()
        calls += (if (audio.exists()) audio.readBytes() else null) to config
        return result
    }
}
