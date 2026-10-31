package com.example.accounting.feature.entry

import com.example.accounting.core.data.speech.SpeechEvent
import com.example.accounting.core.data.speech.SpeechSource
import com.example.accounting.core.data.speech.SpeechStartResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Scripted [SpeechSource]. [startResult] is returned by [start]; tests push events with [emit]. */
class FakeSpeechSource(var startResult: SpeechStartResult = SpeechStartResult.STARTED) : SpeechSource {
    private val flow = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 16)
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
        check(flow.tryEmit(event)) { "FakeSpeechSource: event dropped" }
    }
}
