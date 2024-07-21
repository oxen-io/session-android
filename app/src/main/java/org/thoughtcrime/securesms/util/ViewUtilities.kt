package org.thoughtcrime.securesms.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.FloatEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.util.Size
import android.util.TypedValue
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DimenRes
import network.loki.messenger.R
import org.session.libsession.utilities.getColorFromAttr
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import androidx.core.graphics.applyCanvas
import org.session.libsignal.utilities.Log
import kotlin.math.roundToInt

fun View.contains(point: PointF): Boolean {
    return hitRect.contains(point.x.toInt(), point.y.toInt())
}

val View.hitRect: Rect
    get()  {
        val rect = Rect()
        getHitRect(rect)
        return rect
    }

@ColorInt
fun Context.getAccentColor() = getColorFromAttr(R.attr.colorAccent)

// Method to grab the appropriate attribute for a message colour.
// Note: This is an attribute, NOT a resource Id - see `getColorResourceIdFromAttr` for that.
@AttrRes
fun getMessageTextColourAttr(messageIsOutgoing: Boolean): Int {
    return if (messageIsOutgoing) R.attr.message_sent_text_color else R.attr.message_received_text_color
}

// Method to get an actual R.id.<SOME_COLOUR> resource Id from an attribute such as R.attr.message_sent_text_color etc.
@ColorRes
fun getColorResourceIdFromAttr(context: Context, attr: Int): Int {
    val outTypedValue = TypedValue()
    val successfullyFoundAttribute = context.theme.resolveAttribute(attr, outTypedValue, true)
    if (successfullyFoundAttribute) { return outTypedValue.resourceId }

    Log.w("ViewUtils", "Could not find colour attribute $attr in theme - using grey as a safe fallback")
    return R.color.gray50
}

fun View.animateSizeChange(@DimenRes startSizeID: Int, @DimenRes endSizeID: Int, animationDuration: Long = 250) {
    val startSize = resources.getDimension(startSizeID)
    val endSize = resources.getDimension(endSizeID)
    animateSizeChange(startSize, endSize)
}

fun View.animateSizeChange(startSize: Float, endSize: Float, animationDuration: Long = 250) {
    val layoutParams = this.layoutParams
    val animation = ValueAnimator.ofObject(FloatEvaluator(), startSize, endSize)
    animation.duration = animationDuration
    animation.addUpdateListener { animator ->
        val size = animator.animatedValue as Float
        layoutParams.width = size.toInt()
        layoutParams.height = size.toInt()
        this.layoutParams = layoutParams
    }
    animation.start()
}

fun View.fadeIn(duration: Long = 150) {
    visibility = View.VISIBLE
    animate().setDuration(duration).alpha(1.0f).start()
}

fun View.fadeOut(duration: Long = 150) {
    animate().setDuration(duration).alpha(0.0f).setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            super.onAnimationEnd(animation)
            visibility = View.GONE
        }
    })
}

fun View.hideKeyboard() {
    val imm = this.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(this.windowToken, 0)
}

fun View.drawToBitmap(config: Bitmap.Config = Bitmap.Config.ARGB_8888, longestWidth: Int = 2000): Bitmap {
    val size = Size(measuredWidth, measuredHeight).coerceAtMost(longestWidth)
    val scale = size.width / measuredWidth.toFloat()

    return Bitmap.createBitmap(size.width, size.height, config).applyCanvas {
        scale(scale, scale)
        translate(-scrollX.toFloat(), -scrollY.toFloat())
        draw(this)
    }
}

fun Size.coerceAtMost(longestWidth: Int): Size =
    (width.toFloat() / height).let { aspect ->
        if (aspect > 1) {
            width.coerceAtMost(longestWidth).let { Size(it, (it / aspect).roundToInt()) }
        } else {
            height.coerceAtMost(longestWidth).let { Size((it * aspect).roundToInt(), it) }
        }
    }

fun EditText.addTextChangedListener(listener: (String) -> Unit) {
    addTextChangedListener(object: SimpleTextWatcher() {
        override fun onTextChanged(text: String) {
            listener(text)
        }
    })
}
