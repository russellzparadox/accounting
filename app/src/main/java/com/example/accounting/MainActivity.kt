package com.example.accounting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.accounting.core.data.config.SettingsRepository
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.ui.theme.AccountingTheme
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var ledgerRepository: LedgerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AccountingTheme {
                AppShell(settingsRepository, ledgerRepository)
            }
        }
    }
}
