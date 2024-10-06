package org.thoughtcrime.securesms.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.fragment.app.Fragment
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale
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

@ExperimentalPermissionsApi
fun PermissionState.isPermanentlyDenied(): Boolean {
    return !status.shouldShowRationale && !status.isGranted
}

fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    throw IllegalStateException("Permissions should be called in the context of an Activity")
}

inline fun <T : View> T.afterMeasured(crossinline block: T.() -> Unit) {
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            if (measuredWidth > 0 && measuredHeight > 0) {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                block()
            }
        }
    })
}

/**
 * This is used to set the test tag that the QA team can use to retrieve an element in appium
 * In order to do so we need to set the testTagsAsResourceId to true, which ideally should be done only once
 * in the root composable, but our app is currently made up of  multiple isolated composables
 * set up in the old activity/fragment view system
 * As such we need to repeat it for every component that wants to use testTag, until such
 * a time as we have one root composable
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.qaTag(tag: String) = semantics { testTagsAsResourceId = true }.testTag(tag)