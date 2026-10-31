package com.example.accounting.feature.entry

import androidx.compose.foundation.BorderStroke
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

/** Hilt entry point. Collects [EntryViewModel.state] and forwards actions. Nothing else. */
@Composable
fun EntryRoute(modifier: Modifier = Modifier) {
    val viewModel: EntryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    EntryScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
        accountName = viewModel::accountName,
    )
}

/**
 * Chat-style entry screen. Stateless apart from the input text and the open sheet/dialog.
 * Every change goes out as an [EntryAction]; nothing is written here.
 */
@Composable
fun EntryScreen(
    state: EntryUiState,
    onAction: (EntryAction) -> Unit,
    modifier: Modifier = Modifier,
    accountName: (String) -> String = { it },
) {
    var input by rememberSaveable { mutableStateOf("") }
    var showManual by rememberSaveable { mutableStateOf(false) }
    var showConfig by rememberSaveable { mutableStateOf(false) }
    val notConfigured = state.items.any { it is ChatItem.Error && it.kind == EntryErrorKind.NOT_CONFIGURED }
    LaunchedEffect(notConfigured) {
        if (notConfigured) showConfig = true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { EntryTopBar(onManualEntry = { showManual = true }) },
        bottomBar = {
            ChatInputBar(
                text = input,
                onTextChange = { input = it },
                onSend = {
                    onAction(EntryAction.Submit(input))
                    input = ""
                },
                sendEnabled = input.isNotBlank() && !state.busy,
                busy = state.busy,
                modifier = Modifier.imePadding().navigationBarsPadding(),
            )
        },
    ) { padding ->
        ChatList(
            state = state,
            onAction = onAction,
            accountName = accountName,
            onConfigure = { showConfig = true },
            onManualEntry = { showManual = true },
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
    if (showConfig) {
        BackendConfigDialog(
            onDismiss = { showConfig = false },
            onSave = { url, token ->
                onAction(EntryAction.SaveBackendConfig(url, token))
                showConfig = false
            },
        )
    }
}

@Composable
private fun EntryTopBar(onManualEntry: () -> Unit) {
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
        TextButton(onClick = onManualEntry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(text = stringResource(R.string.action_manual_entry), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ChatList(
    state: EntryUiState,
    onAction: (EntryAction) -> Unit,
    accountName: (String) -> String,
    onConfigure: () -> Unit,
    onManualEntry: () -> Unit,
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
                onConfigure = onConfigure,
                onManualEntry = onManualEntry,
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
    onConfigure: () -> Unit,
    onManualEntry: () -> Unit,
) {
    when (item) {
        is ChatItem.UserText -> UserBubble(item.text)
        is ChatItem.Clarification -> ClarificationBubble(item, onAction)
        is ChatItem.DraftCard -> ConfirmationCard(card = item, onAction = onAction, accountName = accountName)
        is ChatItem.Error -> ErrorBubble(item, onAction, onConfigure, onManualEntry)
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
    onConfigure: () -> Unit,
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
                        OutlinedButton(onClick = onConfigure, modifier = FullWidthButtonModifier) {
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
