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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.accounting.feature.entry.EntryRoute
import com.example.accounting.feature.ledger.AccountsRoute
import com.example.accounting.feature.ledger.JournalRoute

/** Bottom destinations, in reading order. In RTL the first item sits at the right edge. */
enum class MainTab(val labelRes: Int, val icon: ImageVector) {
    ENTRY(R.string.nav_entry, Icons.Filled.Add),
    JOURNAL(R.string.nav_journal, Icons.Filled.Menu),
    ACCOUNTS(R.string.nav_accounts, Icons.Filled.AccountCircle),
}

/**
 * Root navigation: three tabs in a bottom bar (state-hoisted, no nav library).
 * Tabs are not kept in composition. EntryViewModel (Activity scope) holds the chat, the draft cards and the unsent
 * input text, so they survive a tab switch. Back from another tab returns to «ثبت».
 */
@Composable
fun AppShell() {
    var tabName by rememberSaveable { mutableStateOf(MainTab.ENTRY.name) }
    val tab = MainTab.valueOf(tabName)
    BackHandler(enabled = tab != MainTab.ENTRY) { tabName = MainTab.ENTRY.name }

    Scaffold(
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
            MainTab.ENTRY -> EntryRoute(modifier)
            MainTab.JOURNAL -> JournalRoute(modifier)
            MainTab.ACCOUNTS -> AccountsRoute(modifier)
        }
    }
}
