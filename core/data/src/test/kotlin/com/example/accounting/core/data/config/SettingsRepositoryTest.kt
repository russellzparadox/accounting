package com.example.accounting.core.data.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.persian.AmountUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun defaultsAreTomanNoYearNotOnboarded() {
        val repo = SettingsRepository(context)
        assertEquals(AmountUnit.TOMAN, repo.current.amountUnit)
        assertNull(repo.current.fiscalYear)
        assertFalse(repo.current.onboardingAccepted)
    }

    @Test
    fun changesArePublishedAndSurviveANewInstance() {
        val repo = SettingsRepository(context)
        repo.setAmountUnit(AmountUnit.RIAL)
        repo.setFiscalYear(1404)
        repo.setOnboardingAccepted(true)

        assertEquals(AmountUnit.RIAL, repo.settings.value.amountUnit)
        val reloaded = SettingsRepository(context).current
        assertEquals(AmountUnit.RIAL, reloaded.amountUnit)
        assertEquals(1404, reloaded.fiscalYear)
        assertTrue(reloaded.onboardingAccepted)
    }

    @Test
    fun clearingFiscalYearRemovesTheChoice() {
        val repo = SettingsRepository(context)
        repo.setFiscalYear(1403)
        repo.setFiscalYear(null)
        assertNull(SettingsRepository(context).current.fiscalYear)
    }
}
