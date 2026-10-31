package com.example.accounting.core.data.speech

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Placeholder for a cloud STT backend. Not wired in v1: every start is refused as unsupported. */
class CloudSpeechSourceStub : SpeechSource {
    override val events: Flow<SpeechEvent> = emptyFlow()

    override fun start(): SpeechStartResult = SpeechStartResult.UNSUPPORTED

    override fun stop() = Unit

    override fun release() = Unit
}
