package com.example.accounting.feature.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.data.speech.PackStatus
import com.example.accounting.core.data.speech.SpeechEngine
import com.example.accounting.core.data.speech.SpeechErrorKind
import com.example.accounting.core.data.speech.SpeechReport
import com.example.accounting.core.data.speech.VoiceErrorRecord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Speech diagnostics report: Persian lines, the error code, and the plain-text clipboard copy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeechReportCardTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val report = SpeechReport(
        apiLevel = 34,
        recognitionAvailable = true,
        onDeviceAvailable = true,
        servicePackages = listOf("com.google.android.tts"),
        faPack = PackStatus.NOT_INSTALLED,
        lastError = VoiceErrorRecord(SpeechErrorKind.LANGUAGE_PACK_MISSING, 13, SpeechEngine.ON_DEVICE),
    )

    @Test
    fun linesUsePersianDigitsAndShowTheLastErrorCodeAndEngine() {
        val lines = speechReportLines(context, report)

        assertTrue(lines.any { it.contains("۳۴") })
        assertTrue(lines.any { it.contains("(کد ۱۳)") })
        assertTrue(lines.any { it.contains("روی گوشی") })
        assertTrue(lines.any { it.contains("نصب نشده") })
        assertFalse(lines.any { it.contains("34") || it.contains("13)") })
    }

    @Test
    fun copyPutsTheWholeReportOnTheClipboardAsPlainText() {
        copySpeechReport(context, report)

        val clipboard = context.getSystemService(ClipboardManager::class.java)!!
        val text = clipboard.primaryClip!!.getItemAt(0).text.toString()
        assertTrue(text.startsWith(context.getString(R.string.speech_report_title)))
        assertTrue(text.contains("(کد ۱۳)"))
        assertTrue(text.contains("com.google.android.tts"))
    }

    @Test
    fun withoutAnErrorTheLineSaysNothingIsRecorded() {
        val lines = speechReportLines(context, report.copy(lastError = null))

        assertTrue(lines.contains(context.getString(R.string.speech_last_error_none)))
    }
}
