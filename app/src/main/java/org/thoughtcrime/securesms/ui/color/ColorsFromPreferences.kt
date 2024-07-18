package org.thoughtcrime.securesms.ui.color

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.BLUE_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.CLASSIC_DARK
import org.session.libsession.utilities.TextSecurePreferences.Companion.CLASSIC_LIGHT
import org.session.libsession.utilities.TextSecurePreferences.Companion.GREEN_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.OCEAN_LIGHT
import org.session.libsession.utilities.TextSecurePreferences.Companion.ORANGE_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.PINK_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.PURPLE_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.RED_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.YELLOW_ACCENT

/**
 * Retrieve the current [Colors] from [TextSecurePreferences] and current system settings.
 */
@Composable
fun TextSecurePreferences.colors(): Colors = lightDarkColors().colors()
private fun TextSecurePreferences.lightDarkColors() = LightDarkColors(isClassic(), isLight(), getFollowSystemSettings(), primaryColor())
private fun TextSecurePreferences.isLight(): Boolean = getThemeStyle() in setOf(CLASSIC_LIGHT, OCEAN_LIGHT)
private fun TextSecurePreferences.isClassic(): Boolean = getThemeStyle() in setOf(CLASSIC_DARK, CLASSIC_LIGHT)
private fun TextSecurePreferences.primaryColor(): Color = when(getSelectedAccentColor()) {
    GREEN_ACCENT -> primaryGreen
    BLUE_ACCENT -> primaryBlue
    PURPLE_ACCENT -> primaryPurple
    PINK_ACCENT -> primaryPink
    RED_ACCENT -> primaryRed
    ORANGE_ACCENT -> primaryOrange
    YELLOW_ACCENT -> primaryYellow
    else -> Color.Unspecified
}
