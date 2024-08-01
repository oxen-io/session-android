package org.session.libsession.utilities

import androidx.annotation.StringRes

fun interface Toaster {
    fun toast(@StringRes stringRes: Int, toastLength: Int, parameters: Map<String, String>)
}