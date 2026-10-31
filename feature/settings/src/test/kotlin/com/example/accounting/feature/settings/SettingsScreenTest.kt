package com.example.accounting.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.feature.settings.onboarding.OnboardingScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp")
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun str(id: Int): String = context.getString(id)

    @Test
    fun choosingRialSendsTheUnitAction() {
        val actions = mutableListOf<SettingsAction>()
        compose.setContent {
            SettingsScreen(
                state = SettingsUi(amountUnit = AmountUnit.TOMAN),
                onAction = { actions += it },
                versionName = "0.1.1",
                onClose = {},
                onExport = {},
                onRestore = {},
                restoreConfirm = false,
                onConfirmRestore = {},
                onDismissRestore = {},
                onRestartApp = {},
            )
        }
        compose.onNodeWithText(str(R.string.unit_rial)).assertIsDisplayed().performClick()
        assertEquals(listOf<SettingsAction>(SettingsAction.SetAmountUnit(AmountUnit.RIAL)), actions)
    }

    @Test
    fun onboardingShowsDisclosureAndAcceptCallsBack() {
        var accepted = 0
        compose.setContent { OnboardingScreen(onAccept = { accepted++ }) }
        compose.onNodeWithText(str(R.string.onboarding_accept)).assertIsDisplayed().performClick()
        assertEquals(1, accepted)
    }
}
