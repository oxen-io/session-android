package org.thoughtcrime.securesms.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.color.LocalColors

private val disabledBorder @Composable get() = BorderStroke(
    width = LocalDimensions.current.borderStroke,
    color = LocalColors.current.disabled
)

interface ButtonType {
    val contentPadding: PaddingValues get() = ButtonDefaults.ContentPadding

    @Composable
    fun border(enabled: Boolean): BorderStroke?
    @Composable
    fun buttonColors(): ButtonColors

    class Outline(
        private val contentColor: Color,
        private val borderColor: Color = contentColor
    ): ButtonType {
        @Composable
        override fun border(enabled: Boolean) = BorderStroke(
            width = LocalDimensions.current.borderStroke,
            color = if (enabled) borderColor else LocalColors.current.disabled
        )
        @Composable
        override fun buttonColors() = ButtonDefaults.buttonColors(
            contentColor = contentColor,
            backgroundColor = Color.Unspecified,
            disabledContentColor = LocalColors.current.disabled,
            disabledBackgroundColor = Color.Unspecified
        )
    }

    object Fill: ButtonType {
        @Composable
        override fun border(enabled: Boolean) = if (enabled) null else disabledBorder
        @Composable
        override fun buttonColors() = ButtonDefaults.buttonColors(
            contentColor = LocalColors.current.background,
            backgroundColor = LocalColors.current.text,
            disabledContentColor = LocalColors.current.disabled,
            disabledBackgroundColor = Color.Unspecified
        )
    }

    object PrimaryFill: ButtonType {
        @Composable
        override fun border(enabled: Boolean) = if (enabled) null else disabledBorder
        @Composable
        override fun buttonColors() = ButtonDefaults.buttonColors(
            contentColor = LocalColors.current.primaryButtonFillText,
            backgroundColor = LocalColors.current.primaryButtonFill,
            disabledContentColor = LocalColors.current.disabled,
            disabledBackgroundColor = Color.Unspecified
        )
    }

    class Borderless(private val color: Color): ButtonType {
        override val contentPadding: PaddingValues
            get() = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        @Composable
        override fun border(enabled: Boolean) = null
        @Composable
        override fun buttonColors() = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            backgroundColor = Color.Transparent,
            disabledContentColor = LocalColors.current.disabled
        )
    }
}
