package org.thoughtcrime.securesms.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import org.session.libsession.utilities.AppTextSecurePreferences

// Globally accessible composition local objects
val LocalColors = compositionLocalOf <ThemeColors> { ClassicDark() }
val LocalType = compositionLocalOf { sessionTypography }

// Once the app is entirely in Compose we won't need these two properties since we'll have
// a single root composable which will get the theme as a state and update the app accordingly
// without having to keep track of these flags, which are sure to cause syncing issues...
// We can remove them once we have a unique Composable root or once we use the sharedPrefs as flows (PR in review)
var selectedTheme: ThemeColors? = null
var systemInDark: Boolean? = null

/**
 * Apply a Material2 compose theme based on user selections in SharedPreferences.
 */
@Composable
fun SessionMaterialTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val preferences = AppTextSecurePreferences(context)

    // set the theme data if it hasn't been done yet
    // or if the user has changed their light/dark system settings in the background
    if(selectedTheme == null ||
        (systemInDark != isSystemInDarkTheme()) && preferences.getFollowSystemSettings()) {
        // Some values can be set from the preferences, and if not should fallback to a default value
        selectedTheme = preferences.getComposeTheme()
    }

    SessionMaterialTheme(colors = selectedTheme ?: ClassicDark()) { content() }
}

/**
 * Apply a given [ThemeColors], and our typography and shapes as a Material 2 Compose Theme.
 **/
@Composable
fun SessionMaterialTheme(
    colors: ThemeColors,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = colors.toMaterialColors(),
        typography = sessionTypography.asMaterialTypography(),
        shapes = sessionShapes,
    ) {
        CompositionLocalProvider(
            LocalColors provides colors,
            LocalType provides sessionTypography,
            LocalContentColor provides colors.text,
            LocalTextSelectionColors provides colors.textSelectionColors,
        ) {
            content()
        }
    }
}

val pillShape = RoundedCornerShape(percent = 50)
val buttonShape = pillShape

val sessionShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp)
)

/**
 * Set the Material 2 theme and a background for Compose Previews.
 */
@Composable
fun PreviewTheme(
    colors: ThemeColors = LocalColors.current,
    content: @Composable () -> Unit
) {
    SessionMaterialTheme(colors) {
        Box(modifier = Modifier.background(color = LocalColors.current.background)) {
            content()
        }
    }
}

// used for previews
class SessionColorsParameterProvider : PreviewParameterProvider<ThemeColors> {
    override val values = sequenceOf(ClassicDark(), ClassicLight(), OceanDark(), OceanLight())
}
