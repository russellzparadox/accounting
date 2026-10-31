package com.example.accounting.core.data.speech

import com.example.accounting.core.data.config.BackendConfig
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * CloudSpeechSource with fake recorder and transcriber. Unconfined dispatchers make the order deterministic.
 * Checks: permission, configuration, busy, upload result mapping, temp file deleted on every path, auto-stop.
 */
class CloudSpeechSourceTest {
    private lateinit var dir: File
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val configured = BackendConfig("https://proxy.example", "tok-123")
    private val recorder = FakeRecorder()
    private val transcriber = FakeTranscriber()
    private var permission = true
    private val seen = mutableListOf<SpeechEvent>()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("stt-test").toFile()
    }

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun source(
        config: BackendConfig = configured,
        maxRecordMillis: Long = 30_000L,
    ): CloudSpeechSource = CloudSpeechSource(
        scope = scope,
        cacheDir = dir,
        config = { config },
        recorder = recorder,
        transcriber = transcriber,
        hasPermission = { permission },
        ioDispatcher = Dispatchers.Unconfined,
        maxRecordMillis = maxRecordMillis,
    )

    private fun collect(s: CloudSpeechSource) {
        scope.launch { s.events.toList(seen) }
    }

    private fun tempFiles(): List<File> = dir.listFiles().orEmpty().toList()

    @Test
    fun startWithoutPermissionIsRefusedAndNothingIsRecorded() {
        permission = false
        val s = source()
        assertEquals(SpeechStartResult.NO_PERMISSION, s.start())
        assertTrue(tempFiles().isEmpty())
    }

    @Test
    fun startWithoutBackendConfigIsNotConfigured() {
        val s = source(config = BackendConfig.NOT_CONFIGURED)
        assertEquals(SpeechStartResult.NOT_CONFIGURED, s.start())
        assertTrue(tempFiles().isEmpty())
    }

    @Test
    fun recorderRefusingToStartIsUnavailableAndLeavesNoFile() {
        recorder.startOk = false
        assertEquals(SpeechStartResult.UNAVAILABLE, source().start())
        assertTrue(tempFiles().isEmpty())
    }

    @Test
    fun stopUploadsTheClipAndEmitsProcessingThenFinal() {
        val s = source()
        collect(s)
        assertEquals(SpeechStartResult.STARTED, s.start())
        s.stop()

        assertEquals(listOf(SpeechEvent.Processing, SpeechEvent.Final("نوشابه")), seen)
        assertEquals(1, transcriber.calls.size)
        assertEquals(RecordedAudio, transcriber.calls.single().first?.decodeToString())
        assertEquals(configured, transcriber.calls.single().second)
        assertTrue("temp file deleted after upload", tempFiles().isEmpty())
    }

    @Test
    fun uploadFailureIsTypedAndTheFileIsStillDeleted() {
        transcriber.result = TranscriptResult.Failed(SpeechErrorKind.CLOUD_UNAUTHORIZED)
        val s = source()
        collect(s)
        s.start()
        s.stop()

        assertEquals(
            listOf(SpeechEvent.Processing, SpeechEvent.Error(SpeechErrorKind.CLOUD_UNAUTHORIZED)),
            seen,
        )
        assertTrue(tempFiles().isEmpty())
    }

    @Test
    fun aRecordingThatCannotBeFinalisedIsNoMatchWithoutUpload() {
        recorder.stopOk = false
        val s = source()
        collect(s)
        s.start()
        s.stop()

        assertEquals(listOf(SpeechEvent.Error(SpeechErrorKind.NO_MATCH)), seen)
        assertTrue(transcriber.calls.isEmpty())
        assertTrue(tempFiles().isEmpty())
    }

    @Test
    fun recordingStopsItselfAtTheCapAndUploads() {
        val s = source(maxRecordMillis = 0L)
        collect(s)
        assertEquals(SpeechStartResult.STARTED, s.start())

        assertEquals(listOf(SpeechEvent.Processing, SpeechEvent.Final("نوشابه")), seen)
        assertTrue(tempFiles().isEmpty())
    }

    @Test
    fun startWhileUploadingIsBusy() {
        // Upload runs unconfined, so simulate "still uploading" with a transcriber that blocks on a latch.
        val latch = java.util.concurrent.CountDownLatch(1)
        val blocking = object : CloudTranscriber {
            override fun transcribe(audio: File, config: BackendConfig): TranscriptResult {
                latch.await()
                return TranscriptResult.Text("ok")
            }
        }
        val s = CloudSpeechSource(
            scope = CoroutineScope(Job() + Dispatchers.Default),
            cacheDir = dir,
            config = { configured },
            recorder = recorder,
            transcriber = blocking,
            hasPermission = { true },
            ioDispatcher = Dispatchers.Unconfined,
        )
        s.start()
        s.stop()
        assertEquals(SpeechStartResult.BUSY, s.start())
        latch.countDown()
        s.release()
    }

    @Test
    fun releaseWhileRecordingDeletesTheFileAndReleasesTheRecorder() {
        val s = source()
        s.start()
        assertEquals(1, tempFiles().size)
        s.release()
        assertTrue(tempFiles().isEmpty())
        assertEquals(1, recorder.released)
        assertFalse(seen.any { it is SpeechEvent.Final })
    }

    private companion object {
        val RecordedAudio: String = FakeRecorder.AUDIO.decodeToString()
    }
}
