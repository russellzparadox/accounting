package com.example.accounting.feature.entry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.example.accounting.core.data.speech.SpeechEngine
import com.example.accounting.core.data.speech.SpeechErrorKind
import com.example.accounting.core.persian.PersianDigits

/**
 * Bottom input bar, RTL order from the start: [mic] [text field] [send]. Status line under the row:
 * "listening" while a session runs, otherwise the last voice error. Voice never submits.
 * While listening the field is read-only (partial text is live-filled) and send is off.
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    sendEnabled: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    listening: Boolean = false,
    voiceError: SpeechErrorKind? = null,
    voiceErrorCode: Int? = null,
    voicePack: PackUi = PackUi.None,
    onMicClick: () -> Unit = {},
    onPackDownload: () -> Unit = {},
    /** Null hides the «تنظیمات ورودی صدا» button (no settings screen to open on this device). */
    onOpenVoiceSettings: (() -> Unit)? = null,
    /** Engine of the running session, shown as a small label while listening. */
    voiceEngine: SpeechEngine? = null,
    /** Cloud recording is being transcribed: mic off, field read-only. */
    voiceProcessing: Boolean = false,
) {
    val sendDescription = stringResource(R.string.input_send_cd)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MicButton(listening = listening, enabled = !busy && !voiceProcessing, onClick = onMicClick)
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
                readOnly = busy || listening || voiceProcessing,
                placeholder = {
                    Text(
                        text = stringResource(R.string.input_hint),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                shape = RoundedCornerShape(28.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (sendEnabled) onSend() }),
            )
            IconButton(
                onClick = onSend,
                enabled = sendEnabled,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = sendDescription },
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null)
                }
            }
        }
        VoiceStatus(
            listening = listening,
            error = voiceError,
            errorCode = voiceErrorCode,
            pack = voicePack,
            onPackDownload = onPackDownload,
            onOpenVoiceSettings = onOpenVoiceSettings,
            engine = voiceEngine,
            processing = voiceProcessing,
        )
    }
}

/** 56dp round mic button (DESIGN 5.2). Idle: primaryContainer. Listening: primary, label says "stop". */
@Composable
private fun MicButton(listening: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (listening) R.string.input_mic_stop_cd else R.string.input_mic_cd)
    val colors = MaterialTheme.colorScheme
    val container = when {
        !enabled -> colors.onSurface.copy(alpha = 0.12f)
        listening -> colors.primary
        else -> colors.primaryContainer
    }
    val tint = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        listening -> colors.onPrimary
        else -> colors.onPrimaryContainer
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = container,
        contentColor = tint,
        modifier = Modifier
            .size(56.dp)
            .semantics { contentDescription = description },
    ) {
        Canvas(modifier = Modifier.size(56.dp).padding(16.dp)) {
            drawMic(tint = tint)
        }
    }
}

/**
 * Status block under the input row. Listening is polite text. An error is text in the error color, followed by
 * a muted «(کد …)» tail for the next bug report. The language-pack line and the settings button attach below it.
 */
@Composable
private fun VoiceStatus(
    listening: Boolean,
    error: SpeechErrorKind?,
    errorCode: Int?,
    pack: PackUi,
    onPackDownload: () -> Unit,
    onOpenVoiceSettings: (() -> Unit)?,
    engine: SpeechEngine?,
    processing: Boolean,
) {
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    if (processing) {
        Text(
            text = stringResource(R.string.voice_processing),
            style = MaterialTheme.typography.bodyMedium,
            color = mutedColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        return
    }
    if (listening) {
        Text(
            text = engine?.let { stringResource(R.string.input_listening_engine, engineLabel(it)) }
                ?: stringResource(R.string.input_listening),
            style = MaterialTheme.typography.bodyMedium,
            color = mutedColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        return
    }
    val kind = error ?: return
    val message = stringResource(kind.messageRes())
    val line = if (errorCode == null) {
        AnnotatedString(message)
    } else {
        buildAnnotatedString {
            append(message)
            append(" ")
            withStyle(SpanStyle(color = mutedColor)) {
                append(stringResource(R.string.voice_error_code, PersianDigits.toPersian(errorCode.toString())))
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        PackLine(pack = pack, onDownload = onPackDownload)
        val open = onOpenVoiceSettings
        val settingsOffer = pack == PackUi.None || pack is PackUi.Failed
        if (open != null && settingsOffer && kind != SpeechErrorKind.NO_PERMISSION) {
            OutlinedButton(onClick = open, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.voice_settings_action))
            }
        }
    }
}

/** Download button and progress for the fa-IR language pack. Nothing for [PackUi.None]. */
@Composable
private fun PackLine(pack: PackUi, onDownload: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (pack) {
        PackUi.None -> Unit
        PackUi.Offer -> Button(onClick = onDownload, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.voice_pack_download_action))
        }
        PackUi.Pending -> PackText(stringResource(R.string.voice_pack_pending), muted)
        PackUi.Started -> PackText(stringResource(R.string.voice_pack_started), muted)
        PackUi.Done -> PackText(stringResource(R.string.voice_pack_done), muted)
        is PackUi.Downloading -> PackText(
            pack.percent?.let {
                stringResource(R.string.voice_pack_downloading_percent, PersianDigits.toPersian(it.toString()))
            } ?: stringResource(R.string.voice_pack_downloading),
            muted,
        )
        is PackUi.Failed -> PackText(
            stringResource(R.string.voice_pack_failed, PersianDigits.toPersian(pack.code.toString())),
            MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PackText(text: String, color: Color) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.padding(top = 4.dp))
}

/** Microphone outline drawn with Canvas (no extended icon set, keeps the APK small). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMic(tint: Color) {
    val stroke = size.width * 0.09f
    drawRoundRect(
        color = tint,
        topLeft = Offset(size.width * 0.35f, size.height * 0.08f),
        size = Size(size.width * 0.3f, size.height * 0.52f),
        cornerRadius = CornerRadius(size.width * 0.15f),
        style = Stroke(width = stroke),
    )
    drawArc(
        color = tint,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(size.width * 0.2f, size.height * 0.3f),
        size = Size(size.width * 0.6f, size.height * 0.5f),
        style = Stroke(width = stroke),
    )
    drawLine(
        color = tint,
        start = Offset(size.width / 2f, size.height * 0.8f),
        end = Offset(size.width / 2f, size.height * 0.95f),
        strokeWidth = stroke,
    )
}

/** Short engine name for the listening line: «ابری» for the cloud, «دستگاه» for the phone's recognizer. */
@Composable
private fun engineLabel(engine: SpeechEngine): String = stringResource(
    if (engine == SpeechEngine.CLOUD) R.string.voice_engine_cloud else R.string.voice_engine_device,
)
