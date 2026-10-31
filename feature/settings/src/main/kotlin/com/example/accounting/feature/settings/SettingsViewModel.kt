package com.example.accounting.feature.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accounting.core.data.config.BackendConfig
import com.example.accounting.core.data.config.ConfigStore
import com.example.accounting.core.data.config.SettingsRepository
import com.example.accounting.core.data.speech.SpeechProbe
import com.example.accounting.core.data.speech.SpeechReport
import com.example.accounting.core.data.speech.VoiceEngine
import com.example.accounting.core.domain.backup.BackupReject
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.persian.JalaliDate
import com.example.accounting.core.persian.JalaliPickerState
import com.example.accounting.feature.settings.backup.BackupManager
import com.example.accounting.feature.settings.backup.BackupResult
import com.example.accounting.feature.settings.backup.BackupStage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

/** What the screen shows. Money and dates are settings values; texts are string resource ids. */
data class SettingsUi(
    val amountUnit: AmountUnit = AmountUnit.TOMAN,
    /** Chosen fiscal year, or the current Jalali year when none is chosen. */
    val fiscalYear: Int = 0,
    val fiscalYearIsCurrent: Boolean = true,
    val baseUrl: String = "",
    val tokenSaved: Boolean = false,
    val connection: ConnectionResult? = null,
    val connectionChecking: Boolean = false,
    @StringRes val backupMessage: Int? = null,
    val backupBusy: Boolean = false,
    /** True after a successful restore: the screen asks the user to restart. */
    val restored: Boolean = false,
    /** Speech diagnostics. Null while the fa-IR check runs. */
    val speech: SpeechReport? = null,
    /** Voice input engine setting (AUTO by default). */
    val voiceEngine: VoiceEngine = VoiceEngine.AUTO,
)

/** Every user action on the settings screen. Stream and file access come in as lambdas from the route. */
sealed interface SettingsAction {
    data class SetAmountUnit(val unit: AmountUnit) : SettingsAction
    data class ShiftFiscalYear(val delta: Int) : SettingsAction
    data object UseCurrentFiscalYear : SettingsAction
    data class SaveBackend(val url: String, val token: String) : SettingsAction
    data object TestConnection : SettingsAction
    data class Export(val openOutput: () -> OutputStream?) : SettingsAction
    data class Restore(val openInput: () -> InputStream?) : SettingsAction
    data object RefreshSpeech : SettingsAction
    data class SetVoiceEngine(val engine: VoiceEngine) : SettingsAction
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val configStore: ConfigStore,
    private val backup: BackupManager,
    private val clock: Clock,
    private val speechProbe: SpeechProbe,
) : ViewModel() {

    private var config: BackendConfig = BackendConfig.NOT_CONFIGURED
    private val _ui = MutableStateFlow(SettingsUi())
    val ui: StateFlow<SettingsUi> = _ui.asStateFlow()

    init {
        refreshSpeech()
        viewModelScope.launch {
            settings.voiceEngine.collect { engine -> _ui.value = _ui.value.copy(voiceEngine = engine) }
        }
        viewModelScope.launch {
            combine(settings.settings, configStore.config) { s, c -> s to c }.collect { (s, c) ->
                config = c
                val current = JalaliDate.from(LocalDate.now(clock)).year
                _ui.value = _ui.value.copy(
                    amountUnit = s.amountUnit,
                    fiscalYear = s.fiscalYear ?: current,
                    fiscalYearIsCurrent = s.fiscalYear == null,
                    baseUrl = c.baseUrl,
                    tokenSaved = c.token.isNotBlank(),
                )
            }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SetAmountUnit -> settings.setAmountUnit(action.unit)
            is SettingsAction.ShiftFiscalYear -> {
                val target = (_ui.value.fiscalYear + action.delta)
                    .coerceIn(JalaliPickerState.MIN_YEAR, JalaliPickerState.MAX_YEAR)
                settings.setFiscalYear(target)
            }
            SettingsAction.UseCurrentFiscalYear -> settings.setFiscalYear(null)
            is SettingsAction.SaveBackend -> saveBackend(action.url, action.token)
            SettingsAction.TestConnection -> testConnection()
            is SettingsAction.Export -> export(action.openOutput)
            is SettingsAction.Restore -> restore(action.openInput)
            SettingsAction.RefreshSpeech -> refreshSpeech()
            is SettingsAction.SetVoiceEngine -> settings.setVoiceEngine(action.engine)
        }
    }

    private fun refreshSpeech() {
        _ui.value = _ui.value.copy(speech = null)
        speechProbe.snapshot { report -> _ui.value = _ui.value.copy(speech = report) }
    }

    /** An empty token field keeps the saved token: the user can change only the address. */
    private fun saveBackend(url: String, token: String) {
        val newToken = token.trim().ifEmpty { config.token }
        configStore.save(BackendConfig(url.trim(), newToken))
        _ui.value = _ui.value.copy(connection = null)
    }

    private fun testConnection() {
        if (_ui.value.connectionChecking) return
        _ui.value = _ui.value.copy(connectionChecking = true, connection = null)
        viewModelScope.launch {
            val result = BackendPing.check(config)
            _ui.value = _ui.value.copy(connectionChecking = false, connection = result)
        }
    }

    private fun export(openOutput: () -> OutputStream?) {
        if (_ui.value.backupBusy) return
        _ui.value = _ui.value.copy(backupBusy = true, backupMessage = null)
        viewModelScope.launch {
            val result = backup.export(openOutput)
            _ui.value = _ui.value.copy(backupBusy = false, backupMessage = messageFor(result))
        }
    }

    private fun restore(openInput: () -> InputStream?) {
        if (_ui.value.backupBusy) return
        _ui.value = _ui.value.copy(backupBusy = true, backupMessage = null)
        viewModelScope.launch {
            val result = backup.restore(openInput)
            _ui.value = _ui.value.copy(
                backupBusy = false,
                backupMessage = messageFor(result),
                restored = result == BackupResult.Ok,
            )
        }
    }

    companion object {
        @StringRes
        fun messageFor(result: BackupResult): Int = when (result) {
            BackupResult.Ok -> com.example.accounting.feature.settings.R.string.backup_done
            is BackupResult.Rejected -> when (result.reason) {
                BackupReject.NOT_SQLITE -> com.example.accounting.feature.settings.R.string.restore_not_backup
                BackupReject.TOO_SMALL -> com.example.accounting.feature.settings.R.string.restore_too_small
                BackupReject.CORRUPT -> com.example.accounting.feature.settings.R.string.restore_corrupt
                BackupReject.WRONG_SCHEMA_VERSION -> com.example.accounting.feature.settings.R.string.restore_wrong_version
                BackupReject.MISSING_TABLES -> com.example.accounting.feature.settings.R.string.restore_not_ledger
            }
            is BackupResult.Failed -> when (result.stage) {
                BackupStage.READ -> com.example.accounting.feature.settings.R.string.backup_read_failed
                BackupStage.WRITE -> com.example.accounting.feature.settings.R.string.backup_write_failed
                BackupStage.SWAP -> com.example.accounting.feature.settings.R.string.restore_swap_failed
            }
        }
    }
}
