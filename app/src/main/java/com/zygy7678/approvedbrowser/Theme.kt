package com.zygy7678.approvedbrowser

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1457E6),
    onPrimary = Color.White,
    secondary = Color(0xFF425466),
    background = Color(0xFFF7F8FB),
    surface = Color.White
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9DB7FF),
    secondary = Color(0xFFB8C4D8),
    background = Color(0xFF101318),
    surface = Color(0xFF171B22)
)

@Composable
fun ApprovedBrowserTheme(
    darkTheme: Boolean = false,
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val base = if (darkTheme) DarkScheme else LightScheme
    val scheme = if (highContrast) {
        if (darkTheme) base.copy(
            primary = Color(0xFFFFFFFF),
            onPrimary = Color.Black,
            background = Color.Black,
            surface = Color(0xFF111111),
            onSurface = Color.White
        ) else base.copy(
            primary = Color.Black,
            onPrimary = Color.White,
            background = Color.White,
            surface = Color.White,
            onSurface = Color.Black
        )
    } else base

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        content = content
    )
}
