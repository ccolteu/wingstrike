package com.example.wingstrike.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val Scheme =
  darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    background = Ink,
    onBackground = Cream,
    surface = Ink,
    onSurface = Cream,
  )

@Composable
fun WingStrikeTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = Scheme, content = content)
}
