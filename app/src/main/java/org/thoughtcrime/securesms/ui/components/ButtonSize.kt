package org.thoughtcrime.securesms.ui.components

import android.annotation.SuppressLint
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.ui.baseBold
import org.thoughtcrime.securesms.ui.extraSmall
import org.thoughtcrime.securesms.ui.extraSmallBold
import org.thoughtcrime.securesms.ui.smallBold

interface ButtonSize {
    @OptIn(ExperimentalMaterialApi::class)
    @SuppressLint("ComposableNaming")
    @Composable fun applyConstraints(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentEnforcement provides false,
            LocalTextStyle provides textStyle,
        ) {
            content()
        }
    }

    val textStyle: TextStyle @Composable get
    val minHeight: Dp
}

object LargeButtonSize: ButtonSize {
    override val textStyle @Composable get() = baseBold
    override val minHeight = 41.dp
}

object MediumButtonSize: ButtonSize {
    override val textStyle @Composable get() = smallBold
    override val minHeight = 34.dp
}

object SlimButtonSize: ButtonSize {
    override val textStyle @Composable get() = extraSmallBold
    override val minHeight = 29.dp
}