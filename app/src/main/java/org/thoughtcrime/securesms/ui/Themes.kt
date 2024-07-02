package org.thoughtcrime.securesms.ui

import android.content.Context
import androidx.annotation.AttrRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.LayoutDirection
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import com.google.android.material.color.MaterialColors
import network.loki.messenger.R

val LocalExtraColors = staticCompositionLocalOf<ExtraColors> { error("No Custom Attribute value provided") }
val LocalPreviewMode = staticCompositionLocalOf { false }


data class ExtraColors(
    val settingsBackground: Color,
    val destructive: Color,
    val prominentButtonColor: Color,
    val warning: Color,
)

/**
 * Converts current Theme to Compose Theme.
 */
@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val extraColors = LocalContext.current.run {
        ExtraColors(
            settingsBackground = getColorFromTheme(R.attr.colorSettingsBackground),
            destructive = Color(getColor(R.color.destructive)),
            prominentButtonColor = getColorFromTheme(R.attr.prominentButtonColor),
            warning = Color(getColor(R.color.warning))
        )
    }

    CompositionLocalProvider(LocalExtraColors provides extraColors) {
        AppCompatTheme {
            content()
        }
    }
}

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
        LocalContext provides ContextThemeWrapper(LocalContext.current, themeResId),
        LocalPreviewMode provides true
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

class BidiPreviewParameterProvider: PreviewParameterProvider<LayoutDirection> {
    override val values: Sequence<LayoutDirection>
        get() = sequenceOf( LayoutDirection.Ltr, LayoutDirection.Rtl)
}
