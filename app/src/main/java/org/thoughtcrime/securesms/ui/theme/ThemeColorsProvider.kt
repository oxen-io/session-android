package org.thoughtcrime.securesms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

fun interface ThemeColorsProvider {
    @Composable
    fun get(): ThemeColors
}

@Suppress("FunctionName")
fun FollowSystemThemeColorsProvider(light: ThemeColors, dark: ThemeColors) = ThemeColorsProvider {
    when {
        isSystemInDarkTheme() -> dark
        else -> light
    }
}

@Suppress("FunctionName")
fun ThemeColorsProvider(colors: ThemeColors) = ThemeColorsProvider { colors }
