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

val AppTypography = Typography(defaultFontFamily = VazirmatnFamily)
