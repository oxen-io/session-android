package org.thoughtcrime.securesms.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions

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
            containerColor = Color.Transparent,
            disabledContentColor = LocalColors.current.disabled,
            disabledContainerColor = Color.Transparent
        )
    }

    object Fill: ButtonType {
        @Composable
        override fun border(enabled: Boolean) = if (enabled) null else disabledBorder
        @Composable
        override fun buttonColors() = ButtonDefaults.buttonColors(
            contentColor = LocalColors.current.background,
            containerColor = LocalColors.current.text,
            disabledContentColor = LocalColors.current.disabled,
            disabledContainerColor = Color.Transparent
        )
    }

    object PrimaryFill: ButtonType {
        @Composable
        override fun border(enabled: Boolean) = if (enabled) null else disabledBorder
        @Composable
        override fun buttonColors() = ButtonDefaults.buttonColors(
            contentColor = LocalColors.current.primaryButtonFillText,
            containerColor = LocalColors.current.primaryButtonFill,
            disabledContentColor = LocalColors.current.disabled,
            disabledContainerColor = Color.Transparent
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
            containerColor = Color.Transparent,
            disabledContentColor = LocalColors.current.disabled,
            disabledContainerColor = Color.Transparent
        )
    }
}
