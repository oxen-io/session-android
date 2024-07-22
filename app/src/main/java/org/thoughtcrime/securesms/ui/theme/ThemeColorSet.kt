package org.thoughtcrime.securesms.ui.theme

/**
 * This class holds two instances of [ThemeColors], [light] representing the [ThemeColors] to use when the system is in a
 * light theme, and [dark] representing the [ThemeColors] to use when the system is in a dark theme.
 */
data class ThemeColorSet(
    val light: ThemeColors,
    val dark: ThemeColors
)