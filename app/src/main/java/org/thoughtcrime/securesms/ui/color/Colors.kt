package org.thoughtcrime.securesms.ui.color

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.primarySurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.base

val LocalColors = staticCompositionLocalOf<Colors> { ClassicDark() }

interface Colors {
    val isLight: Boolean
    val primary: Color
    val danger: Color
    val disabled: Color
    val background: Color
    val backgroundSecondary: Color
    val text: Color
    val textSecondary: Color
    val borders: Color
    val textBubbleSent: Color
    val backgroundBubbleReceived: Color
    val textBubbleReceived: Color
    val backgroundBubbleSent: Color get() = primary
    val qrCodeContent: Color
    val qrCodeBackground: Color
    val primaryButtonFill: Color
    val primaryButtonFillText: Color
}

fun Colors.text(isError: Boolean): Color = if (isError) danger else text
fun Colors.textSecondary(isError: Boolean): Color = if (isError) danger else textSecondary
fun Colors.borders(isError: Boolean): Color = if (isError) danger else borders

val Colors.textSelectionColors get() = TextSelectionColors(
    handleColor = primary,
    backgroundColor = primary.copy(alpha = 0.5f)
)

data class ClassicDark(override val primary: Color = primaryGreen): Colors {
    override val isLight = false
    override val danger = dangerDark
    override val disabled = disabledDark
    override val background = classicDark0
    override val backgroundSecondary = classicDark1
    override val text = classicDark6
    override val textSecondary = classicDark5
    override val borders = classicDark3
    override val textBubbleSent = Color.Black
    override val backgroundBubbleReceived = classicDark2
    override val textBubbleReceived = Color.White
    override val qrCodeContent = background
    override val qrCodeBackground = text
    override val primaryButtonFill = primary
    override val primaryButtonFillText = Color.Black
}

data class ClassicLight(override val primary: Color = primaryGreen): Colors {
    override val isLight = true
    override val danger = dangerLight
    override val disabled = disabledLight
    override val background = classicLight6
    override val backgroundSecondary = classicLight5
    override val text = classicLight0
    override val textSecondary = classicLight1
    override val borders = classicLight3
    override val textBubbleSent = Color.Black
    override val backgroundBubbleReceived = classicLight4
    override val textBubbleReceived = classicLight4
    override val qrCodeContent = text
    override val qrCodeBackground = backgroundSecondary
    override val primaryButtonFill = text
    override val primaryButtonFillText = Color.White
}

data class OceanDark(override val primary: Color = primaryBlue): Colors {
    override val isLight = false
    override val danger = dangerDark
    override val disabled = disabledDark
    override val background = oceanDark2
    override val backgroundSecondary = oceanDark1
    override val text = oceanDark7
    override val textSecondary = oceanDark5
    override val borders = oceanDark4
    override val textBubbleSent = Color.Black
    override val backgroundBubbleReceived = oceanDark4
    override val textBubbleReceived = oceanDark4
    override val qrCodeContent = background
    override val qrCodeBackground = text
    override val primaryButtonFill = primary
    override val primaryButtonFillText = Color.Black
}

data class OceanLight(override val primary: Color = primaryBlue): Colors {
    override val isLight = true
    override val danger = dangerLight
    override val disabled = disabledLight
    override val background = oceanLight7
    override val backgroundSecondary = oceanLight6
    override val text = oceanLight1
    override val textSecondary = oceanLight2
    override val borders = oceanLight3
    override val textBubbleSent = oceanLight1
    override val backgroundBubbleReceived = oceanLight4
    override val textBubbleReceived = oceanLight1
    override val qrCodeContent = text
    override val qrCodeBackground = backgroundSecondary
    override val primaryButtonFill = text
    override val primaryButtonFillText = Color.White
}

@Composable
fun Colors(name: String, colors: List<Color>) {
    Column {
        colors.forEachIndexed { i, it ->
            Box(Modifier.background(it)) {
                Text("$name: $i")
            }
        }
    }
}

@Preview
@Composable
fun PreviewThemeColors(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: Colors
) {
    PreviewTheme(colors) { ThemeColors() }
}

@Composable
private fun ThemeColors() {
    Column {
        Box(Modifier.background(MaterialTheme.colors.primary)) {
            Text("primary", style = base)
        }
        Box(Modifier.background(MaterialTheme.colors.primaryVariant)) {
            Text("primaryVariant", style = base)
        }
        Box(Modifier.background(MaterialTheme.colors.secondary)) {
            Text("secondary", style = base)
        }
        Box(Modifier.background(MaterialTheme.colors.secondaryVariant)) {
            Text("secondaryVariant", style = base)
        }
        Box(Modifier.background(MaterialTheme.colors.surface)) {
            Text("surface", style = base)
        }
        Box(Modifier.background(MaterialTheme.colors.primarySurface)) {
            Text("primarySurface", style = base)
        }
        Box(Modifier.background(MaterialTheme.colors.background)) {
            Text("background", style = base)
        }
        Box(Modifier.background(MaterialTheme.colors.error)) {
            Text("error", style = base)
        }
    }
}

@Composable
fun Colors.outlinedTextFieldColors(
    isError: Boolean
) = TextFieldDefaults.outlinedTextFieldColors(
    textColor = if (isError) danger else text,
    cursorColor = if (isError) danger else text,
    focusedBorderColor = borders,
    unfocusedBorderColor = borders,
    placeholderColor = if (isError) danger else textSecondary
)

val Colors.divider get() = text.copy(alpha = TabRowDefaults.DividerOpacity)

@Composable
fun Colors.radioButtonColors() = RadioButtonDefaults.colors(
    selectedColor = primary,
    unselectedColor = text,
    disabledColor = disabled
)

@Composable
fun transparentButtonColors() = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent)

@Composable
fun destructiveButtonColors() = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent, contentColor = LocalColors.current.danger)


/**
 * This class holds two instances of [Colors], [light] representing the [Colors] to use when the system is in a
 * light theme, and [dark] representing the [Colors] to use when the system is in a dark theme.
 *
 * If the user has [followSystemSettings] turned on then [light] should be equal to [dark].
 */
data class LightDarkColors(
    val light: Colors,
    val dark: Colors
) {
    @Composable
    fun colors() = if (light == dark || isSystemInDarkTheme()) dark else light
}

/**
 * Courtesy constructor that sets [light] and [dark] based on properties.
 */
fun LightDarkColors(isClassic: Boolean, isLight: Boolean, followSystemSettings: Boolean, primaryOrUnspecified: Color): LightDarkColors {
    val primary = primaryOrUnspecified.takeOrElse { if (isClassic) primaryGreen else primaryBlue }
    val light = when {
        isLight || followSystemSettings -> if (isClassic) ClassicLight(primary) else OceanLight(primary)
        else -> if (isClassic) ClassicDark(primary) else OceanDark(primary)
    }
    val dark = when {
        isLight && !followSystemSettings -> if (isClassic) ClassicLight(primary) else OceanLight(primary)
        else -> if (isClassic) ClassicDark(primary) else OceanDark(primary)
    }
    return LightDarkColors(light, dark)
}
