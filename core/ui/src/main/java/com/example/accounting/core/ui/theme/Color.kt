package com.example.accounting.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Neutral placeholder palette. Real brand palette lands in T01b (design system).
private val White = Color(0xFFFFFFFF)
private val Gray100 = Color(0xFFF2F2F2)
private val Gray200 = Color(0xFFE0E0E0)
private val Gray800 = Color(0xFF333333)
private val Gray900 = Color(0xFF1A1A1A)

val LightColors = lightColorScheme(
    primary = Gray800,
    onPrimary = White,
    background = White,
    onBackground = Gray900,
    surface = White,
    onSurface = Gray900,
)

val DarkColors = darkColorScheme(
    primary = Gray200,
    onPrimary = Gray900,
    background = Gray900,
    onBackground = Gray100,
    surface = Gray900,
    onSurface = Gray100,
)
