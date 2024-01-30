package org.thoughtcrime.securesms.util

import android.content.res.Resources
import android.os.Build
import androidx.annotation.ColorRes
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.roundToInt

fun Resources.getColorWithID(@ColorRes id: Int, theme: Resources.Theme?): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        getColor(id, theme)
    } else {
        @Suppress("DEPRECATION") getColor(id)
    }
}

fun toPx(dp: Int, resources: Resources): Int {
    return toPx(dp.toFloat(), resources).roundToInt()
}

fun toPx(dp: Float, resources: Resources): Float {
    val scale = resources.displayMetrics.density
    return (dp * scale)
}

fun toDp(px: Int, resources: Resources): Int {
    return toDp(px.toFloat(), resources).roundToInt()
}

fun toDp(px: Float, resources: Resources): Float {
    val scale = resources.displayMetrics.density
    return (px / scale)
}

val RecyclerView.isScrolledToBottom: Boolean
    get() = computeVerticalScrollOffset().coerceAtLeast(0) +
            computeVerticalScrollExtent() +
            toPx(50, resources) >= computeVerticalScrollRange()

// Extension property to determine if we should scroll down in the conversation view to show new
// messages or not. Note: The 30dp value is taken from the JIRA issue at:
// https://optf.atlassian.net/browse/SES-1145
val RecyclerView.shouldScrollToBottomMessages: Boolean
    get() = computeVerticalScrollOffset().coerceAtLeast(0) +
            computeVerticalScrollExtent() +
            toPx(30, resources) >= computeVerticalScrollRange()
