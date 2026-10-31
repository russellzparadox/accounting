package com.example.accounting.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.persian.PersianDigits
import com.example.accounting.feature.settings.onboarding.PrivacyPoints
import java.time.LocalDate

/** Hilt entry point: owns the document launchers (they need an Activity) and forwards to [SettingsScreen]. */
@Composable
fun SettingsRoute(
    onClose: () -> Unit,
    onRestartApp: () -> Unit,
    versionName: String,
    focusBackend: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingRestore by rememberSaveable { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            viewModel.onAction(SettingsAction.Export { context.contentResolver.openOutputStream(uri, "wt") })
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingRestore = uri
    }
    SettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        versionName = versionName,
        onClose = onClose,
        onExport = { exportLauncher.launch("accounting-backup-${LocalDate.now()}.db") },
        onRestore = { restoreLauncher.launch(arrayOf("*/*")) },
        restoreConfirm = pendingRestore != null,
        onConfirmRestore = {
            val uri = pendingRestore
            pendingRestore = null
            if (uri != null) {
                viewModel.onAction(SettingsAction.Restore { context.contentResolver.openInputStream(uri) })
            }
        },
        onDismissRestore = { pendingRestore = null },
        onRestartApp = onRestartApp,
        focusBackend = focusBackend,
        modifier = modifier,
    )
}

/**
 * Stateless settings screen. Every change goes out as a [SettingsAction] or a callback.
 * [focusBackend]: scroll once to the backend («سرور حسابداری») section, used when the entry screen asks for a server.
 */
@Composable
fun SettingsScreen(
    state: SettingsUi,
    onAction: (SettingsAction) -> Unit,
    versionName: String,
    onClose: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    restoreConfirm: Boolean,
    onConfirmRestore: () -> Unit,
    onDismissRestore: () -> Unit,
    onRestartApp: () -> Unit,
    focusBackend: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    // Offset of the backend section inside the scrolling column, measured once after the first layout.
    var backendTop by remember { mutableStateOf<Int?>(null) }
    var focusPending by rememberSaveable { mutableStateOf(focusBackend) }
    LaunchedEffect(focusPending, backendTop) {
        val top = backendTop
        if (focusPending && top != null) {
            scroll.animateScrollTo(top)
            focusPending = false
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.settings_back_cd))
            }
        }
        UnitSection(unit = state.amountUnit, onSelect = { onAction(SettingsAction.SetAmountUnit(it)) })
        FiscalSection(state = state, onAction = onAction)
        Column(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                if (backendTop == null) backendTop = coordinates.positionInParent().y.toInt()
            },
        ) {
            BackendSection(state = state, onAction = onAction)
        }
        BackupSection(state = state, onExport = onExport, onRestore = onRestore)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.section_theme))
            BodyText(stringResource(R.string.theme_help))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.section_about))
            BodyText(stringResource(R.string.about_app))
            BodyText(stringResource(R.string.about_version, versionName))
        }
        SpeechReportCard(report = state.speech, onRefresh = { onAction(SettingsAction.RefreshSpeech) })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.section_privacy))
            OutlinedButton(onClick = { showPrivacy = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.privacy_show))
            }
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text(stringResource(R.string.privacy_show)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { PrivacyPoints() } },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) { Text(stringResource(R.string.privacy_close)) }
            },
        )
    }
    if (restoreConfirm) {
        AlertDialog(
            onDismissRequest = onDismissRestore,
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmRestore, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.restore_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRestore, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (state.restored) {
        // Not dismissable: the database is closed, the only next step is the restart.
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.restore_done_title)) },
            text = { Text(stringResource(R.string.restore_done_body)) },
            confirmButton = {
                TextButton(onClick = onRestartApp, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.restore_done_action))
                }
            },
        )
    }
}

@Composable
private fun UnitSection(unit: AmountUnit, onSelect: (AmountUnit) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.section_unit))
        BodyText(stringResource(R.string.unit_help))
        OptionRow(stringResource(R.string.unit_toman), selected = unit == AmountUnit.TOMAN) {
            onSelect(AmountUnit.TOMAN)
        }
        OptionRow(stringResource(R.string.unit_rial), selected = unit == AmountUnit.RIAL) {
            onSelect(AmountUnit.RIAL)
        }
    }
}

@Composable
private fun FiscalSection(state: SettingsUi, onAction: (SettingsAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.section_fiscal))
        val year = PersianDigits.toPersian(state.fiscalYear.toString())
        BodyText(
            stringResource(R.string.fiscal_year_label, year) +
                if (state.fiscalYearIsCurrent) " (${stringResource(R.string.fiscal_current_note)})" else "",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onAction(SettingsAction.ShiftFiscalYear(-1)) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.fiscal_prev)) }
            OutlinedButton(
                onClick = { onAction(SettingsAction.ShiftFiscalYear(1)) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.fiscal_next)) }
            TextButton(
                onClick = { onAction(SettingsAction.UseCurrentFiscalYear) },
                enabled = !state.fiscalYearIsCurrent,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.fiscal_reset)) }
        }
    }
}

@Composable
private fun BackendSection(state: SettingsUi, onAction: (SettingsAction) -> Unit) {
    var url by rememberSaveable(state.baseUrl) { mutableStateOf(state.baseUrl) }
    var token by rememberSaveable { mutableStateOf("") }
    var showToken by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.section_backend))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.backend_url_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.backend_token_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
            supportingText = {
                if (state.tokenSaved) Text(stringResource(R.string.backend_token_saved))
            },
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (showToken) R.string.backend_hide else R.string.backend_show))
                }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onAction(SettingsAction.SaveBackend(url, token))
                    token = ""
                },
                enabled = url.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.backend_save)) }
            OutlinedButton(
                onClick = { onAction(SettingsAction.TestConnection) },
                enabled = !state.connectionChecking,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.backend_test)) }
        }
        BodyText(stringResource(R.string.backend_test_hint))
        val message = when {
            state.connectionChecking -> stringResource(R.string.backend_testing)
            state.connection != null -> stringResource(connectionMessage(state.connection))
            else -> null
        }
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun BackupSection(state: SettingsUi, onExport: () -> Unit, onRestore: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.section_backup))
        BodyText(stringResource(R.string.backup_help))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onExport,
                enabled = !state.backupBusy,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.backup_export)) }
            OutlinedButton(
                onClick = onRestore,
                enabled = !state.backupBusy,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.backup_restore)) }
        }
        if (state.backupBusy) BodyText(stringResource(R.string.backup_busy))
        state.backupMessage?.let { id ->
            Text(
                text = stringResource(id),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun BodyText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private fun connectionMessage(result: ConnectionResult): Int = when (result) {
    ConnectionResult.OK -> R.string.conn_ok
    ConnectionResult.UNAUTHORIZED -> R.string.conn_unauthorized
    ConnectionResult.RATE_LIMITED -> R.string.conn_rate_limited
    ConnectionResult.SERVER_ERROR -> R.string.conn_server
    ConnectionResult.UNREACHABLE -> R.string.conn_unreachable
    ConnectionResult.NOT_CONFIGURED -> R.string.conn_not_configured
}
