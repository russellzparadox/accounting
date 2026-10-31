package com.example.accounting

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.accounting.core.data.config.SettingsRepository
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.persian.JalaliDate
import com.example.accounting.core.ui.money.LocalAmountUnit
import com.example.accounting.feature.entry.EntryRoute
import com.example.accounting.feature.ledger.AccountsRoute
import com.example.accounting.feature.ledger.JournalRoute
import com.example.accounting.feature.ledger.ManualJournalRoute
import com.example.accounting.feature.reports.ReportsRoute
import com.example.accounting.feature.settings.SettingsRoute
import com.example.accounting.feature.settings.onboarding.OnboardingScreen
import com.example.accounting.feature.settings.restartApp
import java.time.LocalDate

/** Bottom destinations, in reading order. In RTL the first item sits at the right edge. */
enum class MainTab(val labelRes: Int, val icon: ImageVector) {
    ENTRY(R.string.nav_entry, Icons.Filled.Add),
    JOURNAL(R.string.nav_journal, Icons.Filled.Menu),
    ACCOUNTS(R.string.nav_accounts, Icons.Filled.AccountCircle),
}

/**
 * Root. Until the privacy disclosure is accepted, only [OnboardingScreen] is shown: nothing can be entered first.
 * After that, three tabs in a bottom bar (state-hoisted, no nav library).
 * Tabs are not kept in composition. EntryViewModel (Activity scope) holds the chat, the draft cards and the unsent
 * input text, so they survive a tab switch. Back from another tab returns to «ثبت».
 *
 * The manual journal editor (سند دستی), reports and settings each replace the tabs while open.
 */
@Composable
fun AppShell(settingsRepository: SettingsRepository, ledger: LedgerRepository) {
    val appSettings by settingsRepository.settings.collectAsState()
    // The fiscal year from Settings (or the current year) is opened at start and again when Settings changes it.
    val todayYear = remember { JalaliDate.from(LocalDate.now()).year }
    val fiscalYearToOpen = appSettings.fiscalYear ?: todayYear
    LaunchedEffect(fiscalYearToOpen) { ledger.ensureFiscalYear(fiscalYearToOpen) }
    // The single source of the amount unit for every screen (see LocalAmountUnit).
    CompositionLocalProvider(LocalAmountUnit provides appSettings.amountUnit) {
        if (!appSettings.onboardingAccepted) {
            Scaffold { padding ->
                OnboardingScreen(
                    onAccept = { settingsRepository.setOnboardingAccepted(true) },
                    modifier = Modifier.padding(padding),
                )
            }
        } else {
            MainShell()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell() {
    var tabName by rememberSaveable { mutableStateOf(MainTab.ENTRY.name) }
    val tab = MainTab.valueOf(tabName)
    var manualOpen by remember { mutableStateOf(false) }
    var manualSeed by remember { mutableStateOf<JournalEntry?>(null) }
    var manualToken by remember { mutableStateOf(0L) }
    var reportsOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    // True when Settings is opened from the entry screen's «تنظیمات سرور» action: the backend section is shown.
    var settingsFocusBackend by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    fun openManual(seed: JournalEntry?) {
        manualSeed = seed
        manualToken += 1
        manualOpen = true
    }
    BackHandler(enabled = !manualOpen && !reportsOpen && !settingsOpen && tab != MainTab.ENTRY) {
        tabName = MainTab.ENTRY.name
    }

    if (manualOpen) {
        Scaffold { padding ->
            ManualJournalRoute(
                seed = manualSeed,
                seedToken = manualToken,
                onClose = { manualOpen = false },
                modifier = Modifier.padding(padding),
            )
        }
    } else if (reportsOpen) {
        Scaffold { padding ->
            ReportsRoute(onClose = { reportsOpen = false }, modifier = Modifier.padding(padding))
        }
    } else if (settingsOpen) {
        Scaffold { padding ->
            SettingsRoute(
                onClose = {
                    settingsOpen = false
                    settingsFocusBackend = false
                },
                onRestartApp = { restartApp(context) },
                versionName = BuildConfig.VERSION_NAME,
                focusBackend = settingsFocusBackend,
                modifier = Modifier.padding(padding),
            )
        }
    } else {
        Scaffold(
            topBar = {
                // Reports and settings on the journal and accounts tabs. The entry tab has its own top bar.
                if (tab != MainTab.ENTRY) {
                    TopAppBar(
                        title = {},
                        actions = {
                            TextButton(onClick = { reportsOpen = true }) { Text(stringResource(R.string.nav_reports)) }
                            TextButton(onClick = {
                                settingsFocusBackend = false
                                settingsOpen = true
                            }) { Text(stringResource(R.string.nav_settings)) }
                        },
                    )
                }
            },
            bottomBar = {
                NavigationBar {
                    MainTab.entries.forEach { option ->
                        NavigationBarItem(
                            selected = option == tab,
                            onClick = { tabName = option.name },
                            icon = { Icon(imageVector = option.icon, contentDescription = null) },
                            label = { Text(stringResource(option.labelRes)) },
                        )
                    }
                }
            },
        ) { padding ->
            val modifier = Modifier.padding(padding)
            when (tab) {
                MainTab.ENTRY -> EntryRoute(
                    modifier,
                    onEditAsDocument = { openManual(it) },
                    onOpenSettings = {
                        settingsFocusBackend = false
                        settingsOpen = true
                    },
                    onOpenBackendSettings = {
                        settingsFocusBackend = true
                        settingsOpen = true
                    },
                )
                MainTab.JOURNAL -> JournalRoute(modifier, onManual = { openManual(null) })
                MainTab.ACCOUNTS -> AccountsRoute(modifier)
            }
        }
    }
}
