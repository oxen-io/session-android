package org.thoughtcrime.securesms.ui

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.ui.theme.SessionMaterialTheme

fun Activity.setComposeContent(content: @Composable () -> Unit) {
    ComposeView(this)
        .apply { setThemedContent(content) }
        .let(::setContentView)
}

fun Fragment.createThemedComposeView(content: @Composable () -> Unit): ComposeView = requireContext().createThemedComposeView(content)
fun Context.createThemedComposeView(content: @Composable () -> Unit): ComposeView = ComposeView(this).apply {
    setThemedContent(content)
}

fun ComposeView.setThemedContent(content: @Composable () -> Unit) = setContent {
    SessionMaterialTheme {
        content()
    }
}
