package org.thoughtcrime.securesms.conversation.v2.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewTypingIndicatorBinding

class TypingIndicatorView : LinearLayout {
    companion object {
        private const val CYCLE_DURATION: Long = 1500
        private const val DOT_DURATION: Long = 600
        private const val MIN_ALPHA = 0.4f
        private const val MIN_SCALE = 0.75f
    }

    private val binding: ViewTypingIndicatorBinding by lazy {
        val binding = ViewTypingIndicatorBinding.bind(this)
        
        if (tint != -1) {
            binding.typingDot1.getBackground().setColorFilter(tint, PorterDuff.Mode.MULTIPLY)
            binding.typingDot2.getBackground().setColorFilter(tint, PorterDuff.Mode.MULTIPLY)
            binding.typingDot3.getBackground().setColorFilter(tint, PorterDuff.Mode.MULTIPLY)
        }

        return@lazy binding
    }

    private var isActive = false
    private var startTime: Long = 0
    private var tint: Int = -1

    constructor(context: Context) : super(context) { initialize(null) }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize(attrs) }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize(attrs) }

    private fun initialize(attrs: AttributeSet?) {
        setWillNotDraw(false)

        if (attrs != null) {
            val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.TypingIndicatorView, 0, 0)
            this.tint = typedArray.getColor(R.styleable.TypingIndicatorView_typingIndicator_tint, Color.WHITE)
            typedArray.recycle()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!isActive) {
            super.onDraw(canvas)
            return
        }
        val timeInCycle = (System.currentTimeMillis() - startTime) % CYCLE_DURATION
        render(binding.typingDot1, timeInCycle, 0)
        render(binding.typingDot2, timeInCycle, 150)
        render(binding.typingDot3, timeInCycle, 300)
        super.onDraw(canvas)
        postInvalidate()
    }

    private fun render(dot: View?, timeInCycle: Long, start: Long) {
        val end = start + DOT_DURATION
        val peak = start + DOT_DURATION / 2
        if (timeInCycle < start || timeInCycle > end) {
            renderDefault(dot)
        } else if (timeInCycle < peak) {
            renderFadeIn(dot, timeInCycle, start)
        } else {
            renderFadeOut(dot, timeInCycle, peak)
        }
    }

    private fun renderDefault(dot: View?) {
        dot!!.alpha = MIN_ALPHA
        dot.scaleX = MIN_SCALE
        dot.scaleY = MIN_SCALE
    }

    private fun renderFadeIn(dot: View?, timeInCycle: Long, fadeInStart: Long) {
        val percent = (timeInCycle - fadeInStart).toFloat() / 300
        dot!!.alpha = MIN_ALPHA + (1 - MIN_ALPHA) * percent
        dot.scaleX = MIN_SCALE + (1 - MIN_SCALE) * percent
        dot.scaleY = MIN_SCALE + (1 - MIN_SCALE) * percent
    }

    private fun renderFadeOut(dot: View?, timeInCycle: Long, fadeOutStart: Long) {
        val percent = (timeInCycle - fadeOutStart).toFloat() / 300
        dot!!.alpha = 1 - (1 - MIN_ALPHA) * percent
        dot.scaleX = 1 - (1 - MIN_SCALE) * percent
        dot.scaleY = 1 - (1 - MIN_SCALE) * percent
    }

    fun startAnimation() {
        isActive = true
        startTime = System.currentTimeMillis()
        postInvalidate()
    }

    fun stopAnimation() {
        isActive = false
    }
}