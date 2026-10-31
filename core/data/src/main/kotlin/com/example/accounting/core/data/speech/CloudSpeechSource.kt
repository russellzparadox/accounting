package com.example.accounting.core.data.speech

import com.example.accounting.core.data.config.BackendConfig
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Cloud engine: records one short clip, uploads it to the backend, emits the transcript as [SpeechEvent.Final].
 * Session: [start] records (auto-stops after [MAX_RECORD_MS]); [stop] ends recording and uploads.
 * Never auto-submits: the text only fills the input, like on-device.
 * The temp file is deleted on every path: upload success, failure, cancellation, [release].
 * Main thread for start/stop/release; the upload runs on [ioDispatcher].
 */
class CloudSpeechSource(
    private val scope: CoroutineScope,
    private val cacheDir: File,
    private val config: () -> BackendConfig,
    private val recorder: AudioRecorder,
    private val transcriber: CloudTranscriber,
    private val hasPermission: () -> Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxRecordMillis: Long = MAX_RECORD_MS,
) : SpeechSource {
    private val mutableEvents = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 16)
    override val events: Flow<SpeechEvent> = mutableEvents.asSharedFlow()

    private var recordingFile: File? = null
    private var autoStop: Job? = null
    private var upload: Job? = null

    override fun start(): SpeechStartResult {
        if (!hasPermission()) return SpeechStartResult.NO_PERMISSION
        if (recordingFile != null || upload?.isActive == true) return SpeechStartResult.BUSY
        if (!config().isConfigured) return SpeechStartResult.NOT_CONFIGURED
        val target = File(cacheDir, "stt-${UUID.randomUUID()}.m4a")
        if (!recorder.start(target)) {
            target.delete()
            return SpeechStartResult.UNAVAILABLE
        }
        recordingFile = target
        autoStop = scope.launch {
            delay(maxRecordMillis)
            stop()
        }
        return SpeechStartResult.STARTED
    }

    override fun stop() {
        val audio = recordingFile ?: return
        recordingFile = null
        autoStop?.cancel()
        autoStop = null
        if (!recorder.stop()) {
            audio.delete()
            emit(SpeechEvent.Error(SpeechErrorKind.NO_MATCH))
            return
        }
        val cfg = config()
        upload = scope.launch {
            emit(SpeechEvent.Processing)
            val result = try {
                withContext(ioDispatcher) { transcriber.transcribe(audio, cfg) }
            } finally {
                audio.delete()
            }
            emit(
                when (result) {
                    is TranscriptResult.Text -> SpeechEvent.Final(result.text)
                    is TranscriptResult.Failed -> SpeechEvent.Error(result.kind)
                },
            )
        }
    }

    override fun release() {
        autoStop?.cancel()
        autoStop = null
        upload?.cancel()
        upload = null
        recorder.release()
        recordingFile?.delete()
        recordingFile = null
    }

    private fun emit(event: SpeechEvent) {
        mutableEvents.tryEmit(event)
    }

    companion object {
        /** Hard cap per recording. Auto-stop, then upload. */
        const val MAX_RECORD_MS = 30_000L
    }
}
