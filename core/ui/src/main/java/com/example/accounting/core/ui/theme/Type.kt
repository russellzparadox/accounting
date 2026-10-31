package com.example.accounting.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.accounting.core.ui.R

// Vazirmatn (SIL OFL 1.1), see core/ui/src/main/assets/licenses/OFL-Vazirmatn.txt
val VazirmatnFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

// Material3 (BOM 2025.10.00) keeps the constructor taking defaultFontFamily internal,
// so set Vazirmatn on each public text style explicitly.
private val M3Defaults = Typography()

val AppTypography = Typography(
    displayLarge = M3Defaults.displayLarge.copy(fontFamily = VazirmatnFamily),
    displayMedium = M3Defaults.displayMedium.copy(fontFamily = VazirmatnFamily),
    displaySmall = M3Defaults.displaySmall.copy(fontFamily = VazirmatnFamily),
    headlineLarge = M3Defaults.headlineLarge.copy(fontFamily = VazirmatnFamily),
    headlineMedium = M3Defaults.headlineMedium.copy(fontFamily = VazirmatnFamily),
    headlineSmall = M3Defaults.headlineSmall.copy(fontFamily = VazirmatnFamily),
    titleLarge = M3Defaults.titleLarge.copy(fontFamily = VazirmatnFamily),
    titleMedium = M3Defaults.titleMedium.copy(fontFamily = VazirmatnFamily),
    titleSmall = M3Defaults.titleSmall.copy(fontFamily = VazirmatnFamily),
    bodyLarge = M3Defaults.bodyLarge.copy(fontFamily = VazirmatnFamily),
    bodyMedium = M3Defaults.bodyMedium.copy(fontFamily = VazirmatnFamily),
    bodySmall = M3Defaults.bodySmall.copy(fontFamily = VazirmatnFamily),
    labelLarge = M3Defaults.labelLarge.copy(fontFamily = VazirmatnFamily),
    labelMedium = M3Defaults.labelMedium.copy(fontFamily = VazirmatnFamily),
    labelSmall = M3Defaults.labelSmall.copy(fontFamily = VazirmatnFamily),
)
