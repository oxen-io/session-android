package org.thoughtcrime.securesms.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import org.session.libsession.utilities.AppTextSecurePreferences
import org.thoughtcrime.securesms.ui.color.ClassicDark
import org.thoughtcrime.securesms.ui.color.ClassicLight
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.color.OceanDark
import org.thoughtcrime.securesms.ui.color.OceanLight
import org.thoughtcrime.securesms.ui.color.colors
import org.thoughtcrime.securesms.ui.color.textSelectionColors

/**
 * Apply a Material2 compose theme based on user selections in SharedPreferences.
 */
@Composable
fun SessionMaterialTheme(
    content: @Composable () -> Unit
) {
    SessionMaterialTheme(LocalContext.current.colors()) { content() }
}

/**
 * Apply a given [Colors], and our typography and shapes as a Material 2 Compose Theme.
 **/
@Composable
fun SessionMaterialTheme(
    colors: Colors,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = colors.toMaterialColors(),
        typography = sessionTypography,
        shapes = sessionShapes,
    ) {
        CompositionLocalProvider(
            LocalColors provides colors,
            LocalContentColor provides colors.text,
            LocalTextSelectionColors provides colors.textSelectionColors,
        ) {
            content()
        }
    }
}

private fun Colors.toMaterialColors() = androidx.compose.material.Colors(
    primary = background,
    primaryVariant = backgroundSecondary,
    secondary = background,
    secondaryVariant = background,
    background = background,
    surface = background,
    error = danger,
    onPrimary = text,
    onSecondary = text,
    onBackground = text,
    onSurface = text,
    onError = text,
    isLight = isLight
)

@Composable private fun Context.colors() = AppTextSecurePreferences(this).colors()

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
    colors: Colors = LocalColors.current,
    content: @Composable () -> Unit
) {
    SessionMaterialTheme(colors) {
        Box(modifier = Modifier.background(color = LocalColors.current.background)) {
            content()
        }
    }
}

class SessionColorsParameterProvider : PreviewParameterProvider<Colors> {
    override val values = sequenceOf(ClassicDark(), ClassicLight(), OceanDark(), OceanLight())
}
