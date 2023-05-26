package org.session.libsession.utilities

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.RecyclerView

@ColorInt
fun Context.getColorFromAttr(
    @AttrRes attrColor: Int,
    typedValue: TypedValue = TypedValue(),
    resolveRefs: Boolean = true
): Int {
    theme.resolveAttribute(attrColor, typedValue, resolveRefs)
    return typedValue.data
}

val RecyclerView.isScrolledToBottom: Boolean
    get() = computeVerticalScrollOffset() + computeVerticalScrollExtent() >= computeVerticalScrollRange()
