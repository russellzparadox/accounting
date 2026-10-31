package com.example.accounting.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// docs/DESIGN.md section 1 (v1 placeholder palette, owner approves before T14).
// Every text pair is >= 4.5:1 on its surface. Keep these hex values in sync with DESIGN.md.

/** Custom semantic colors (not M3 slots). Provided by [AccountingTheme] via [LocalAccountingColors]. */
data class AccountingColors(val income: Color, val expense: Color)

val LightAccountingColors = AccountingColors(income = Color(0xFF17653A), expense = Color(0xFFA8322A))
val DarkAccountingColors = AccountingColors(income = Color(0xFF7DD39A), expense = Color(0xFFF2A59E))

val LightColors = lightColorScheme(
    primary = Color(0xFF1B5A85),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E4F1),
    onPrimaryContainer = Color(0xFF0B2C43),
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF141A22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141A22),
    surfaceVariant = Color(0xFFE6EBF2),
    onSurfaceVariant = Color(0xFF424B59),
    outline = Color(0xFF6F7885),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF9CCBEF),
    onPrimary = Color(0xFF0A2739),
    primaryContainer = Color(0xFF1D4764),
    onPrimaryContainer = Color(0xFFD3E4F1),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE3E8EE),
    surface = Color(0xFF171D24),
    onSurface = Color(0xFFE3E8EE),
    surfaceVariant = Color(0xFF2A323D),
    onSurfaceVariant = Color(0xFFC3CBD6),
    outline = Color(0xFF8E97A4),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF5C1310),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)
