package org.thoughtcrime.securesms.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.session.libsession.utilities.ExpirationUtil
import kotlin.time.Duration

/**
 * Compatibility class to allow ViewModels to use strings and string resources interchangeably.
 */
sealed class GetString {

    @Composable
    operator fun invoke() = string()
    operator fun invoke(context: Context) = string(context)

    @Composable
    abstract fun string(): String

    abstract fun string(context: Context): String
    data class FromString(val string: String): GetString() {
        @Composable
        override fun string(): String = string
        override fun string(context: Context): String = string
    }
    data class FromResId(@StringRes val resId: Int): GetString() {
        @Composable
        override fun string(): String = stringResource(resId)
        override fun string(context: Context): String = context.getString(resId)
    }
    data class FromFun(val function: (Context) -> String): GetString() {
        @Composable
        override fun string(): String = function(LocalContext.current)
        override fun string(context: Context): String = function(context)
    }

    data class FromMap<T>(val value: T, val function: (Context, T) -> String): GetString() {
        @Composable
        override fun string(): String = function(LocalContext.current, value)

        override fun string(context: Context): String = function(context, value)
    }
}

fun GetString(@StringRes resId: Int) = GetString.FromResId(resId)
fun GetString(string: String) = GetString.FromString(string)
fun GetString(function: (Context) -> String) = GetString.FromFun(function)
fun <T> GetString(value: T, function: (Context, T) -> String) = GetString.FromMap(value, function)
fun GetString(duration: Duration) = GetString.FromMap(duration, ExpirationUtil::getExpirationDisplayValue)


/**
 * Represents some text with an associated title.
 */
data class TitledText(val title: GetString, val text: String) {
    constructor(title: String, text: String): this(GetString(title), text)
    constructor(@StringRes title: Int, text: String): this(GetString(title), text)
}
