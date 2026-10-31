package com.example.accounting.core.data.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.example.accounting.core.data.config.BackendConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The voice input engine for one voice view model: on-device/system ([AndroidSpeechSource]) plus cloud
 * ([CloudSpeechSource]), routed by [VoicePreferences] ([RoutingSpeechSource]). Callers release it in onCleared.
 * Main thread only.
 */
fun createVoiceSpeech(
    context: Context,
    prefs: VoicePreferences,
    backend: () -> BackendConfig,
): SpeechSource {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val hasMic = { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
    val cloud = CloudSpeechSource(
        scope = scope,
        cacheDir = context.cacheDir,
        config = backend,
        recorder = MediaRecorderAudioRecorder(context),
        transcriber = TranscriptionHttp(),
        hasPermission = hasMic,
    )
    return RoutingSpeechSource(
        local = AndroidSpeechSource(context),
        cloud = cloud,
        prefs = prefs,
        scope = scope,
    )
}
