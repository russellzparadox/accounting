package com.example.accounting.feature.entry

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.accounting.core.data.speech.SpeechErrorKind

/** Which permission sheet is open. RATIONALE asks; DENIED_FOREVER sends the user to app settings. */
enum class MicSheet { RATIONALE, DENIED_FOREVER }

/** RECORD_AUDIO is granted right now. Read directly, so a grant made in settings is seen at the next tap. */
fun hasMicPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Opens this app's page in system settings, where the user can switch the microphone permission on. */
fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}

/** Persian line shown under the input bar for a voice error (DESIGN 5.2: errors are text, not only sound). */
fun SpeechErrorKind.messageRes(): Int = when (this) {
    SpeechErrorKind.NO_MATCH -> R.string.input_voice_unclear
    SpeechErrorKind.UNAVAILABLE -> R.string.voice_error_unavailable
    SpeechErrorKind.BUSY -> R.string.voice_error_busy
    SpeechErrorKind.NO_PERMISSION -> R.string.voice_error_no_permission
    SpeechErrorKind.NETWORK -> R.string.voice_error_network
    SpeechErrorKind.UNSUPPORTED -> R.string.voice_error_unsupported
    SpeechErrorKind.OTHER -> R.string.voice_error_generic
}

/** Bottom sheet for the microphone permission. The system dialog is opened only from [onRequest]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicPermissionSheet(
    sheet: MicSheet,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.mic_rationale_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(
                    if (sheet == MicSheet.RATIONALE) R.string.mic_permission_body else R.string.mic_permission_denied_body,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
            )
            when (sheet) {
                MicSheet.RATIONALE -> Button(onClick = onRequest, modifier = FullWidthButtonModifier) {
                    Text(stringResource(R.string.mic_permission_action))
                }
                MicSheet.DENIED_FOREVER -> Button(onClick = onOpenSettings, modifier = FullWidthButtonModifier) {
                    Text(stringResource(R.string.mic_settings_action))
                }
            }
            TextButton(onClick = onDismiss, modifier = FullWidthButtonModifier) {
                Text(stringResource(R.string.mic_permission_later_action))
            }
        }
    }
}
