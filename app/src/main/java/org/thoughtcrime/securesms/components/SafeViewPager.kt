package org.thoughtcrime.securesms.components

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

/**
 * An extension of ViewPager to swallow erroneous multi-touch exceptions.
 *
 * @see https://stackoverflow.com/questions/6919292/pointerindex-out-of-range-android-multitouch
 */
class SafeViewPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewPager(context, attrs) {
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean = try {
        super.onTouchEvent(event)
    } catch (e: IllegalArgumentException) {
        false
    }

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean = try {
        super.onInterceptTouchEvent(event)
    } catch (e: IllegalArgumentException) {
        false
    }
}
