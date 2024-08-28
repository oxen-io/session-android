package org.thoughtcrime.securesms.ui

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.squareup.phrase.Phrase
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

// Extension method to use the Phrase library to substitute strings & return a CharSequence.
// The pair is the key name, such as APP_NAME_KEY and the value is the localised string, such as context.getString(R.string.app_name).
// Note: We cannot have Pair<String, Int> versions of this or the `getSubbedString` method because the JVM sees the signatures as identical.
fun Context.getSubbedCharSequence(stringId: Int, vararg substitutionPairs: Pair<String, String>): CharSequence {
    val phrase = Phrase.from(this, stringId)
    for ((key, value) in substitutionPairs) { phrase.put(key, value) }
    return phrase.format()
}

// Extension method to use the Phrase library to substitute strings & return the substituted String.
// The pair is the key name, such as APP_NAME_KEY and the value is the localised string, such as context.getString(R.string.app_name).
fun Context.getSubbedString(stringId: Int, vararg substitutionPairs: Pair<String, String>): String {
    return getSubbedCharSequence(stringId, *substitutionPairs).toString()
}

fun ComposeView.setThemedContent(content: @Composable () -> Unit) = setContent {
    SessionMaterialTheme {
        content()
    }
}
