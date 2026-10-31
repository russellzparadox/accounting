package com.example.accounting.core.domain.settings

import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.persian.JalaliPickerState

/**
 * User settings as the app reads them. Stored raw in SharedPreferences (core:data `SettingsRepository`) and decoded here.
 * [fiscalYear] is the Jalali year the reports and ledger open on; null means "current year" (the caller decides).
 */
data class AppSettings(
    val amountUnit: AmountUnit = AmountUnit.TOMAN,
    val fiscalYear: Int? = null,
    val onboardingAccepted: Boolean = false,
) {
    companion object {
        val DEFAULT = AppSettings()
    }
}

/** Raw stored values to [AppSettings]. Unknown or out-of-range values fall back to the default. Never throws. */
object AppSettingsCodec {
    /** Stored name of a unit. Kept stable: changing it loses the user's choice. */
    fun unitName(unit: AmountUnit): String = unit.name

    fun decode(unitName: String?, fiscalYear: Int?, onboardingAccepted: Boolean): AppSettings = AppSettings(
        amountUnit = AmountUnit.entries.firstOrNull { it.name == unitName } ?: AmountUnit.TOMAN,
        fiscalYear = fiscalYear?.takeIf { it in JalaliPickerState.MIN_YEAR..JalaliPickerState.MAX_YEAR },
        onboardingAccepted = onboardingAccepted,
    )
}
