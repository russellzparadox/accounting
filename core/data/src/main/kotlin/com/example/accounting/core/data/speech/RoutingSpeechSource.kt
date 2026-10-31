package com.example.accounting.core.data.speech

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Picks the engine per the voice setting and forwards only the active engine's events.
 *  - [VoiceEngine.DEVICE_ONLY]: on-device/system only.
 *  - [VoiceEngine.CLOUD_ONLY]: cloud only.
 *  - [VoiceEngine.AUTO]: on-device/system first. If it fails with NO_RECOGNIZER_INSTALLED,
 *    LANGUAGE_PACK_MISSING or NETWORK, and the cloud is configured and consented, the cloud runs
 *    for this session (the local error is not shown). Other errors are shown as they are.
 * Consent is checked by the view model before [start] (it needs a user tap); this class only checks it for the fallback.
 * Main thread only. [release] cancels the internal collectors.
 */
class RoutingSpeechSource(
    private val local: SpeechSource,
    private val cloud: SpeechSource,
    private val prefs: VoicePreferences,
    private val scope: CoroutineScope,
) : SpeechSource {
    private val mutableEvents = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 16)
    override val events: Flow<SpeechEvent> = mutableEvents.asSharedFlow()

    /** The engine whose session is running. Events from the other engine are dropped. */
    private var active: SpeechSource? = null

    init {
        scope.launch { local.events.collect { onLocal(it) } }
        scope.launch { cloud.events.collect { if (active === cloud) mutableEvents.tryEmit(it) } }
    }

    override fun start(): SpeechStartResult = when (prefs.engine) {
        VoiceEngine.DEVICE_ONLY -> startLocal()
        VoiceEngine.CLOUD_ONLY -> startCloud()
        VoiceEngine.AUTO -> {
            val result = startLocal()
            // No recognition service at all: the cloud can still serve this session.
            if (result == SpeechStartResult.UNAVAILABLE && canUseCloud()) startCloud() else result
        }
    }

    override fun stop() {
        active?.stop()
    }

    override fun release() {
        active = null
        local.release()
        cloud.release()
        scope.cancel()
    }

    private fun startLocal(): SpeechStartResult {
        active = local
        val result = local.start()
        if (result != SpeechStartResult.STARTED) active = null
        return result
    }

    private fun startCloud(): SpeechStartResult {
        active = cloud
        val result = cloud.start()
        if (result == SpeechStartResult.STARTED) {
            mutableEvents.tryEmit(SpeechEvent.EngineChanged(SpeechEngine.CLOUD))
        } else {
            active = null
        }
        return result
    }

    private fun onLocal(event: SpeechEvent) {
        if (active !== local) return
        if (event is SpeechEvent.Error && prefs.engine == VoiceEngine.AUTO &&
            event.kind in FALLBACK_KINDS && canUseCloud()
        ) {
            // Fall back once. The local error is not shown; the cloud attempt reports its own result.
            active = null
            when (startCloud()) {
                SpeechStartResult.STARTED -> Unit
                else -> mutableEvents.tryEmit(SpeechEvent.Error(event.kind, event.code))
            }
            return
        }
        mutableEvents.tryEmit(event)
    }

    private fun canUseCloud(): Boolean = prefs.cloudConfigured && prefs.cloudConsented

    companion object {
        /** Local failures that the cloud engine can recover from. */
        val FALLBACK_KINDS: Set<SpeechErrorKind> = setOf(
            SpeechErrorKind.NO_RECOGNIZER_INSTALLED,
            SpeechErrorKind.LANGUAGE_PACK_MISSING,
            SpeechErrorKind.NETWORK,
        )
    }
}
