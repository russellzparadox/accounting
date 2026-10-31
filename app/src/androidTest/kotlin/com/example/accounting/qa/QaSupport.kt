package com.example.accounting.qa

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.accounting.core.data.db.AppDatabase
import java.io.File
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * QA output folder. Pulled by the `instrumented` workflow with
 * `adb pull /sdcard/Android/data/com.example.accounting/files/qa` (app external files dir, `qa/`).
 */
object QaOutput {
    fun dir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "qa").apply { mkdirs() }
    }

    /** Full-device screenshot through UiAutomation. Fails the test if the PNG is not written. */
    fun screenshot(context: Context, name: String): File {
        val file = File(dir(context), "$name.png")
        val ok = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(file)
        check(ok && file.length() > 0L) { "screenshot $name was not written to ${file.path}" }
        return file
    }
}

/**
 * Clean app state before the test, and before its Activity is launched (this rule is ordered first).
 * Removes the Room file (and its WAL/SHM), the non-secret settings prefs and the export cache.
 * Only valid when no earlier test in this process opened the app's database; see docs/CI.md.
 */
class CleanAppStateRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.deleteDatabase(AppDatabase.FILE_NAME)
            // Same name as SettingsRepository.PREFS_NAME (private there).
            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().clear().commit()
            java.io.File(context.cacheDir, "reports").deleteRecursively()
            base.evaluate()
        }
    }
}
