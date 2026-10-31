package com.example.accounting.feature.settings

import android.content.Context
import android.content.Intent

/**
 * Relaunches the app's main screen in a new process. Used after a restore, because the live Room database
 * is closed and replaced; reopening it in place would leave other ViewModels holding a closed database.
 */
fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
    }
    Runtime.getRuntime().exit(0)
}
