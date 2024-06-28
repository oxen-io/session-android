package org.thoughtcrime.securesms.ui

import android.content.Context
import androidx.annotation.AttrRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.google.accompanist.themeadapter.appcompat.createAppCompatTheme
import com.google.android.material.color.MaterialColors
import network.loki.messenger.R

val LocalExtraColors = staticCompositionLocalOf<ExtraColors> { error("No Custom Attribute value provided") }


data class ExtraColors(
    val settingsBackground: Color,
    val prominentButtonColor: Color,
    val lightCell: Color,
    val onLightCell: Color,
)

/**
 * Converts current Theme to Compose Theme.
 */
@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val extraColors = context.run {
        ExtraColors(
            settingsBackground = getColorFromTheme(R.attr.colorSettingsBackground),
            prominentButtonColor = getColorFromTheme(R.attr.prominentButtonColor),
            lightCell = getColorFromTheme(R.attr.lightCell),
            onLightCell = getColorFromTheme(R.attr.onLightCell),
        )
    }

    val surface = context.getColorFromTheme(R.attr.colorSettingsBackground)


    CompositionLocalProvider(LocalExtraColors provides extraColors) {
        AppCompatTheme(surface = surface) {
            CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(
                handleColor = MaterialTheme.colors.secondary,
                backgroundColor = MaterialTheme.colors.secondary.copy(alpha = 0.5f)
            )) {
                content()
            }
        }
    }
}

@Composable
fun AppCompatTheme(
    context: Context = LocalContext.current,
    readColors: Boolean = true,
    typography: Typography = sessionTypography,
    shapes: Shapes = MaterialTheme.shapes,
    surface: Color? = null,
    content: @Composable () -> Unit
) {
    val themeParams = remember(context.theme) {
        context.createAppCompatTheme(
            readColors = readColors,
            readTypography = false
        )
    }

    val colors = themeParams.colors ?: MaterialTheme.colors

    MaterialTheme(
        colors = colors.copy(
            surface = surface ?: colors.surface
        ),
        typography = typography,
        shapes = shapes,
    ) {
        // We update the LocalContentColor to match our onBackground. This allows the default
        // content color to be more appropriate to the theme background
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colors.onBackground,
            content = content
        )
    }
}

fun boldStyle(size: TextUnit) = TextStyle.Default.copy(
    fontWeight = FontWeight.Bold,
    fontSize = size
)

fun defaultStyle(size: TextUnit) = TextStyle.Default.copy(fontSize = size)

val sessionTypography = Typography(
    h1 = boldStyle(36.sp),
    h2 = boldStyle(32.sp),
    h3 = boldStyle(29.sp),
    h4 = boldStyle(26.sp),
    h5 = boldStyle(23.sp),
    h6 = boldStyle(20.sp),
)

val Typography.base get() = defaultStyle(14.sp)
val Typography.baseBold get() = boldStyle(14.sp)
val Typography.small get() = defaultStyle(12.sp)
val Typography.extraSmall get() = defaultStyle(11.sp)

val Typography.h7 get() = boldStyle(18.sp)
val Typography.h8 get() = boldStyle(16.sp)
val Typography.h9 get() = boldStyle(14.sp)

fun Context.getColorFromTheme(@AttrRes attr: Int, defaultValue: Int = 0x0): Color =
    MaterialColors.getColor(this, attr, defaultValue).let(::Color)

/**
 * Set the theme and a background for Compose Previews.
 */
@Composable
fun PreviewTheme(
    themeResId: Int,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalContext provides ContextThemeWrapper(LocalContext.current, themeResId)
    ) {
        AppTheme {
            Box(modifier = Modifier.background(color = MaterialTheme.colors.background)) {
                content()
            }
        }
    }
}

class ThemeResPreviewParameterProvider : PreviewParameterProvider<Int> {
    override val values = sequenceOf(
        R.style.Classic_Dark,
        R.style.Classic_Light,
        R.style.Ocean_Dark,
        R.style.Ocean_Light,
    )
}
