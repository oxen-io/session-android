package org.thoughtcrime.securesms.ui.components

import android.annotation.SuppressLint
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.ui.baseBold
import org.thoughtcrime.securesms.ui.extraSmall
import org.thoughtcrime.securesms.ui.extraSmallBold

interface ButtonStyle {
    @OptIn(ExperimentalMaterialApi::class)
    @SuppressLint("ComposableNaming")
    @Composable fun applyButtonConstraints(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentEnforcement provides false,
            content = content
        )
    }

    @SuppressLint("ComposableNaming")
    @Composable fun applyTextConstraints(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalTextStyle provides textStyle,
            content = content
        )
    }

    val textStyle: TextStyle
    val minHeight: Dp

    object Large: ButtonStyle {
        override val textStyle = baseBold.copy(textAlign = TextAlign.Center)
        override val minHeight = 41.dp
    }

    object Slim: ButtonStyle {
        override val textStyle = extraSmallBold.copy(textAlign = TextAlign.Center)
        override val minHeight = 29.dp
    }

    object Borderless: ButtonStyle {
        override val textStyle = extraSmall.copy(textAlign = TextAlign.Center)
        override val minHeight = 37.dp
    }
}
