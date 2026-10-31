package com.example.accounting.qa

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.accounting.core.data.db.AppDatabase
import java.io.File
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * QA output folder: the app's INTERNAL `files/qa` (`targetContext.filesDir`). Always non-null, no scoped-storage
 * limits. Pulled by `.github/scripts/pull-qa.sh` with `adb exec-out run-as com.example.accounting tar c -C files qa`
 * (the debug build is debuggable). The absolute path is logged under tag `QA` for diagnosis.
 */
object QaOutput {
    fun dir(context: Context): File {
        val qa = File(context.filesDir, "qa").apply { mkdirs() }
        Log.i("QA", "output dir: ${qa.absolutePath}")
        return qa
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
