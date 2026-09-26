package com.zygy7678.approvedbrowser

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppScheme = lightColorScheme(
    primary = Color(0xFF1457E6),
    onPrimary = Color.White,
    background = Color(0xFFF7F8FB),
    surface = Color.White
)
@Composable
fun ApprovedBrowserTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppScheme, typography = Typography(), content = content)
}
