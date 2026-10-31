package com.example.accounting.core.domain.settings

import com.example.accounting.core.persian.AmountUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppSettingsCodecTest {
    @Test
    fun defaultsAreTomanNoFiscalYearNotOnboarded() {
        assertEquals(AppSettings(), AppSettingsCodec.decode(null, null, false))
        assertEquals(AppSettings.DEFAULT, AppSettingsCodec.decode(null, null, false))
    }

    @Test
    fun storedUnitRoundTrips() {
        for (unit in AmountUnit.entries) {
            val decoded = AppSettingsCodec.decode(AppSettingsCodec.unitName(unit), null, false)
            assertEquals(unit, decoded.amountUnit)
        }
    }

    @Test
    fun unknownUnitFallsBackToToman() {
        assertEquals(AmountUnit.TOMAN, AppSettingsCodec.decode("DOLLAR", null, false).amountUnit)
        assertEquals(AmountUnit.TOMAN, AppSettingsCodec.decode("rial", null, false).amountUnit)
    }

    @Test
    fun fiscalYearOutOfRangeIsDropped() {
        assertEquals(1404, AppSettingsCodec.decode(null, 1404, false).fiscalYear)
        assertNull(AppSettingsCodec.decode(null, 1299, false).fiscalYear)
        assertNull(AppSettingsCodec.decode(null, 1501, false).fiscalYear)
    }

    @Test
    fun onboardingFlagPassesThrough() {
        assertEquals(true, AppSettingsCodec.decode(null, null, true).onboardingAccepted)
    }
}
