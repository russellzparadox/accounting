package com.example.accounting.core.ui.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.persian.MoneyFormat

/**
 * The one amount unit (toman or rial) every money display and money input reads.
 * AppShell provides it from SettingsRepository, so a change in Settings reaches every screen.
 * The default is only for previews and for tests that do not provide it; production always provides.
 */
val LocalAmountUnit = compositionLocalOf { AmountUnit.TOMAN }

/** Rial value as shown in the unit from [LocalAmountUnit]. Use this for every displayed amount. */
@Composable
fun amountText(rial: Long): String = MoneyFormat.format(rial, LocalAmountUnit.current)
