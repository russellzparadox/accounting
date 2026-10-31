package com.example.accounting.feature.entry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp

/**
 * Bottom input bar, RTL order from the start: [mic] [text field] [send].
 * Voice is T09: the mic slot is reserved and disabled.
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    sendEnabled: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    val micDescription = stringResource(
        R.string.input_mic_disabled_cd,
        stringResource(R.string.input_mic_cd),
        stringResource(R.string.input_mic_soon),
    )
    val sendDescription = stringResource(R.string.input_send_cd)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = {},
            enabled = false,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = micDescription },
        ) {
            MicGlyph(tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp),
            readOnly = busy,
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
}

/** Microphone outline drawn with Canvas (no extended icon set, keeps the APK small). */
@Composable
private fun MicGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
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
}
