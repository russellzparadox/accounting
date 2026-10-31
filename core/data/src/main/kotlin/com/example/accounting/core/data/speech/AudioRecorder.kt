package com.example.accounting.core.data.speech

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records one mono AAC clip. Behind an interface so the cloud source is testable without a microphone. */
interface AudioRecorder {
    /** Starts recording to [file]. False when the recorder cannot start (no microphone, busy, bad state). */
    fun start(file: File): Boolean

    /** Stops and finalises the file. False when nothing usable was recorded (for example stopped at once). */
    fun stop(): Boolean

    /** Frees the recorder. Safe to call at any time. */
    fun release()
}

/** [MediaRecorder] writing MPEG-4/AAC, 16 kHz mono, 64 kbps: small enough for a 30 s upload. Caller holds RECORD_AUDIO. */
class MediaRecorderAudioRecorder(private val context: Context) : AudioRecorder {
    private var recorder: MediaRecorder? = null

    override fun start(file: File): Boolean {
        val next = newRecorder()
        return try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioEncodingBitRate(BIT_RATE)
            next.setAudioSamplingRate(SAMPLE_RATE)
            next.setAudioChannels(1)
            next.setOutputFile(file.absolutePath)
            next.prepare()
            next.start()
            recorder = next
            true
        } catch (e: Exception) {
            next.release()
            false
        }
    }

    override fun stop(): Boolean {
        val current = recorder ?: return false
        recorder = null
        return try {
            current.stop()
            true
        } catch (e: RuntimeException) {
            // Stopped before any audio was written: the platform throws. Treated as nothing recorded.
            false
        } finally {
            current.release()
        }
    }

    override fun release() {
        recorder?.let { runCatching { it.release() } }
        recorder = null
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

    private companion object {
        const val BIT_RATE = 64_000
        const val SAMPLE_RATE = 16_000
    }
}
