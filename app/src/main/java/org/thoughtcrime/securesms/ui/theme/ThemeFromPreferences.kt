package org.thoughtcrime.securesms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.BLUE_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.ORANGE_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.PINK_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.PURPLE_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.RED_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.YELLOW_ACCENT


/**
 * Returns the compose theme based on saved preferences
 * Some behaviour is hardcoded to cater for legacy usage of people with themes already set
 * But future themes will be picked and set directly from the "Appearance" screen
 */
@Composable
fun TextSecurePreferences.getComposeTheme(): ThemeColors {
    val selectedTheme = getThemeStyle()

    // get the chosen primary color from the preferences
    val selectedPrimary = primaryColor()

    // create a theme set with the appropriate primary
    val colorSet = when(selectedTheme){
        TextSecurePreferences.OCEAN_DARK,
        TextSecurePreferences.OCEAN_LIGHT -> ThemeColorSet(
            light = OceanLight(selectedPrimary),
            dark = OceanDark(selectedPrimary)
        )

        else -> ThemeColorSet(
            light = ClassicLight(selectedPrimary),
            dark = ClassicDark(selectedPrimary)
        )
    }

    // deliver the right set from the light/dark mode chosen
    val theme = when{
        getFollowSystemSettings() -> if(isSystemInDarkTheme()) colorSet.dark else colorSet.light

        selectedTheme == TextSecurePreferences.CLASSIC_LIGHT ||
                selectedTheme == TextSecurePreferences.OCEAN_LIGHT -> colorSet.light

        else -> colorSet.dark
    }

    return theme
}

fun TextSecurePreferences.primaryColor(): Color = when(getSelectedAccentColor()) {
    BLUE_ACCENT -> primaryBlue
    PURPLE_ACCENT -> primaryPurple
    PINK_ACCENT -> primaryPink
    RED_ACCENT -> primaryRed
    ORANGE_ACCENT -> primaryOrange
    YELLOW_ACCENT -> primaryYellow
    else -> primaryGreen
}



