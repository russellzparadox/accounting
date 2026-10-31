package com.example.accounting.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.accounting.core.ui.R

// Vazirmatn (SIL OFL 1.1), see core/ui/src/main/assets/licenses/OFL-Vazirmatn.txt
val VazirmatnFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

// docs/DESIGN.md section 2. Only Regular, Medium, Bold exist as static files.
// Named public params only (BOM 2025.10.00 hides the defaultFontFamily constructor).
// letterSpacing = 0 everywhere: Persian must not be letter-spaced.
private fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = VazirmatnFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
)

val AppTypography = Typography(
    displayLarge = style(56, 64, FontWeight.Bold),
    displayMedium = style(40, 52, FontWeight.Bold),
    displaySmall = style(36, 44, FontWeight.Bold),
    headlineLarge = style(32, 40, FontWeight.Bold),
    headlineMedium = style(28, 36, FontWeight.Bold),
    headlineSmall = style(24, 32, FontWeight.Bold),
    titleLarge = style(20, 28, FontWeight.Bold),
    titleMedium = style(16, 24, FontWeight.Medium),
    titleSmall = style(14, 20, FontWeight.Medium),
    bodyLarge = style(16, 26, FontWeight.Normal),
    bodyMedium = style(14, 22, FontWeight.Normal),
    bodySmall = style(12, 18, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(12, 18, FontWeight.Medium),
)

/** Custom money style (DESIGN.md 2): all money values in lists and lines. */
object AccountingTypography {
    val amount: TextStyle = style(18, 26, FontWeight.Medium)
}
