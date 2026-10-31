package com.example.accounting.feature.entry

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.accounting.core.persian.PersianDigits
import com.example.accounting.core.domain.JournalEntry

/** Hilt entry point. Collects [EntryViewModel.state] and forwards actions. Nothing else. */
@Composable
fun EntryRoute(
    modifier: Modifier = Modifier,
    onEditAsDocument: (JournalEntry) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenBackendSettings: () -> Unit = {},
) {
    val viewModel: EntryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val voiceModel: VoiceViewModel = hiltViewModel()
    val voice by voiceModel.state.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    // Recognized text fills the field. It is never sent here: the user taps send. Re-entering the tab re-delivers the
    // last transcript; EntryViewModel ignores it because its seq was already applied.
    LaunchedEffect(voice.transcript) {
        voice.transcript?.let(viewModel::onVoiceTranscript)
    }
    // Leaving the foreground ends a listening session; the recognizer is released in VoiceViewModel.onCleared.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, voiceModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) voiceModel.onAction(VoiceAction.Stop)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    EntryScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
        accountName = viewModel::accountName,
        voice = voice,
        onVoiceAction = voiceModel::onAction,
        input = input,
        onInputChange = viewModel::onInputChange,
        onEditAsDocument = onEditAsDocument,
        onOpenSettings = onOpenSettings,
        onOpenBackendSettings = onOpenBackendSettings,
    )
}

/**
 * Chat-style entry screen. The unsent input text comes in as [input] (held by EntryViewModel). Only the open
 * sheet is local state. The backend URL and token are edited only in Settings: a NOT_CONFIGURED error offers
 * «تنظیمات سرور», which calls [onOpenBackendSettings].
 * Amounts use the unit from LocalAmountUnit.
 * Every change goes out as an [EntryAction]; nothing is written here.
 */
@Composable
fun EntryScreen(
    state: EntryUiState,
    onAction: (EntryAction) -> Unit,
    modifier: Modifier = Modifier,
    accountName: (String) -> String = { it },
    voice: VoiceUiState = VoiceUiState(),
    onVoiceAction: (VoiceAction) -> Unit = {},
    input: String = "",
    onInputChange: (String) -> Unit = {},
    onEditAsDocument: (JournalEntry) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenBackendSettings: () -> Unit = {},
) {
    var showManual by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    var micSheet by rememberSaveable { mutableStateOf<MicSheet?>(null) }
    var micPermanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            micPermanentlyDenied = false
            onVoiceAction(VoiceAction.Start(input))
        } else {
            // First denial: the system allows asking again. After that (or "don't ask again"), it does not.
            val canAskAgain = context.findActivity()
                ?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true
            micPermanentlyDenied = !canAskAgain
            micSheet = if (canAskAgain) null else MicSheet.DENIED_FOREVER
        }
    }
    val onMicClick: () -> Unit = {
        if (voice.listening) {
            onVoiceAction(VoiceAction.Stop)
        } else if (hasMicPermission(context)) {
            onVoiceAction(VoiceAction.Start(input))
        } else {
            micSheet = if (micPermanentlyDenied) MicSheet.DENIED_FOREVER else MicSheet.RATIONALE
        }
    }

    // Settings button only when the phone has a voice settings screen (checked once per composition).
    val voiceSettingsAction: (() -> Unit)? = remember(context) {
        if (canOpenVoiceSettings(context)) ({ openVoiceSettings(context) }) else null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { EntryTopBar(onManualEntry = { showManual = true }, onOpenSettings = onOpenSettings) },
        bottomBar = {
            ChatInputBar(
                text = input,
                onTextChange = onInputChange,
                onSend = {
                    onAction(EntryAction.Submit(input))
                    onInputChange("")
                },
                sendEnabled = input.isNotBlank() && !state.busy && !voice.listening,
                busy = state.busy,
                listening = voice.listening,
                voiceError = voice.error,
                voiceErrorCode = voice.errorCode,
                voicePack = voice.pack,
                onMicClick = onMicClick,
                onPackDownload = { onVoiceAction(VoiceAction.DownloadLanguagePack) },
                onOpenVoiceSettings = voiceSettingsAction,
                modifier = Modifier.imePadding().navigationBarsPadding(),
            )
        },
    ) { padding ->
        ChatList(
            state = state,
            onAction = onAction,
            accountName = accountName,
            onOpenBackendSettings = onOpenBackendSettings,
            onManualEntry = { showManual = true },
            onEditAsDocument = onEditAsDocument,
            modifier = Modifier.padding(padding),
        )
    }

    if (showManual) {
        ManualEntrySheet(
            onDismiss = { showManual = false },
            onSubmit = { action ->
                onAction(action)
                showManual = false
            },
        )
    }
    micSheet?.let { sheet ->
        MicPermissionSheet(
            sheet = sheet,
            onRequest = {
                micSheet = null
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            },
            onOpenSettings = {
                micSheet = null
                openAppSettings(context)
            },
            onDismiss = { micSheet = null },
        )
    }
}

@Composable
private fun EntryTopBar(onManualEntry: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.entry_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(text = stringResource(R.string.action_settings), style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = onManualEntry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(text = stringResource(R.string.action_manual_entry), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ChatList(
    state: EntryUiState,
    onAction: (EntryAction) -> Unit,
    accountName: (String) -> String,
    onOpenBackendSettings: () -> Unit,
    onManualEntry: () -> Unit,
    onEditAsDocument: (JournalEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val total = state.items.size + if (state.busy) 1 else 0
    LaunchedEffect(total) {
        if (total > 0) listState.animateScrollToItem(total - 1)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.items.isEmpty() && !state.busy) {
            item(key = "empty") { EmptyHint() }
        }
        items(items = state.items, key = { it.id }) { item ->
            ChatRow(
                item = item,
                onAction = onAction,
                accountName = accountName,
                onOpenBackendSettings = onOpenBackendSettings,
                onManualEntry = onManualEntry,
                onEditAsDocument = onEditAsDocument,
            )
        }
        if (state.busy) {
            item(key = "busy") { BusyRow() }
        }
    }
}

@Composable
private fun ChatRow(
    item: ChatItem,
    onAction: (EntryAction) -> Unit,
    accountName: (String) -> String,
    onOpenBackendSettings: () -> Unit,
    onManualEntry: () -> Unit,
    onEditAsDocument: (JournalEntry) -> Unit,
) {
    when (item) {
        is ChatItem.UserText -> UserBubble(item.text)
        is ChatItem.Clarification -> ClarificationBubble(item, onAction)
        is ChatItem.DraftCard -> ConfirmationCard(
            card = item,
            onAction = onAction,
            accountName = accountName,
            onEditAsDocument = onEditAsDocument,
        )
        is ChatItem.Error -> ErrorBubble(item, onAction, onOpenBackendSettings, onManualEntry)
        is ChatItem.Committed -> CommittedBubble(item, onAction)
        is ChatItem.Reversed -> ReversedBubble(item)
    }
}

@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.entry_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.entry_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BusyRow() {
    Row(
        modifier = Modifier.heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.loading_parse),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun ClarificationBubble(item: ChatItem.Clarification, onAction: (EntryAction) -> Unit) {
    var answer by rememberSaveable(item.id) { mutableStateOf("") }
    ChatCard {
        Text(
            text = item.question,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.ask_answer_hint)) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
            shape = RoundedCornerShape(12.dp),
            maxLines = 3,
        )
        Button(
            onClick = {
                onAction(EntryAction.AnswerClarification(answer))
                answer = ""
            },
            enabled = answer.isNotBlank(),
            modifier = FullWidthButtonModifier,
        ) {
            Text(stringResource(R.string.action_answer))
        }
    }
}

@Composable
private fun ErrorBubble(
    item: ChatItem.Error,
    onAction: (EntryAction) -> Unit,
    onOpenBackendSettings: () -> Unit,
    onManualEntry: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            Text(text = item.message, style = MaterialTheme.typography.bodyMedium)
            item.actions.forEach { action ->
                when (action) {
                    ErrorAction.RETRY -> {
                        if (item.retryText != null) {
                            Button(
                                onClick = { onAction(EntryAction.Retry(item.id)) },
                                modifier = FullWidthButtonModifier,
                            ) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                    ErrorAction.CONFIGURE_SERVER -> {
                        OutlinedButton(onClick = onOpenBackendSettings, modifier = FullWidthButtonModifier) {
                            Text(stringResource(R.string.action_configure_server))
                        }
                    }
                    ErrorAction.MANUAL_ENTRY -> {
                        OutlinedButton(onClick = onManualEntry, modifier = FullWidthButtonModifier) {
                            Text(stringResource(R.string.action_manual_entry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommittedBubble(item: ChatItem.Committed, onAction: (EntryAction) -> Unit) {
    val docNumber = PersianDigits.toPersian(item.doc.docNumber.toString())
    ChatCard {
        Text(
            text = stringResource(R.string.committed_notice, docNumber),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (item.reversed) {
            StatusBadge(stringResource(R.string.row_reversed_badge))
        } else {
            OutlinedButton(
                onClick = { onAction(EntryAction.Undo(item.doc.id)) },
                modifier = FullWidthButtonModifier,
            ) {
                Text(stringResource(R.string.reverse_action))
            }
        }
    }
}

@Composable
private fun ReversedBubble(item: ChatItem.Reversed) {
    val docNumber = PersianDigits.toPersian(item.doc.docNumber.toString())
    ChatCard {
        Text(
            text = stringResource(R.string.reversed_notice, docNumber),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StatusBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Assistant-side card: surface, 1dp outline, 16dp corners, 16dp padding (DESIGN 5.3). */
@Composable
internal fun ChatCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Full-width action button with the 48dp minimum touch height (DESIGN 5.7). */
internal val FullWidthButtonModifier: Modifier = Modifier
    .fillMaxWidth()
    .heightIn(min = 48.dp)
