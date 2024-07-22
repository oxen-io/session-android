package org.thoughtcrime.securesms.ui.components

import android.annotation.SuppressLint
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.bold

interface ButtonStyle {
    @OptIn(ExperimentalMaterial3Api::class)
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
            LocalTextStyle provides textStyle(),
            content = content
        )
    }

    @Composable
    fun textStyle() : TextStyle

    val minHeight: Dp

    object Large: ButtonStyle {
        @Composable
        override fun textStyle() = LocalType.current.base.bold()
            .copy(textAlign = TextAlign.Center)
        override val minHeight = 41.dp
    }

    object Slim: ButtonStyle {
        @Composable
        override fun textStyle() = LocalType.current.extraSmall.bold()
            .copy(textAlign = TextAlign.Center)
        override val minHeight = 29.dp
    }

    object Borderless: ButtonStyle {
        @Composable
        override fun textStyle() = LocalType.current.extraSmall
            .copy(textAlign = TextAlign.Center)
        override val minHeight = 37.dp
    }
}
