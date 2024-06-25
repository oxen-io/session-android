package org.thoughtcrime.securesms.conversation.v2.input_bar

import android.animation.FloatEvaluator
import android.animation.IntEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewInputBarRecordingBinding
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.animateSizeChange
import org.thoughtcrime.securesms.util.disableClipping
import org.thoughtcrime.securesms.util.toPx
import java.util.Date

class InputBarRecordingView : RelativeLayout {
    private lateinit var binding: ViewInputBarRecordingBinding
    private var startTimestamp = 0L
    private var dotViewAnimation: ValueAnimator? = null
    private var pulseAnimation: ValueAnimator? = null
    var delegate: InputBarRecordingViewDelegate? = null
    private var timerJob: Job? = null

    val lockView: LinearLayout
        get() = binding.lockView

    val chevronImageView: ImageView
        get() = binding.inputBarChevronImageView

    val slideToCancelTextView: TextView
        get() = binding.inputBarSlideToCancelTextView

    val recordButtonOverlay: RelativeLayout
        get() = binding.recordButtonOverlay

    // Static constants for animation durations in milliseconds
    companion object {
        val AnimateLockDurationMS = 250L
        val DotAnimationDurationMS = 500L
        val DotPulseAnimationDurationMS = 1000L
        val ShowHideDurationMS = 250L
    }

    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        binding = ViewInputBarRecordingBinding.inflate(LayoutInflater.from(context), this, true)
        binding.inputBarMiddleContentContainer.disableClipping()
        binding.inputBarCancelButton.setOnClickListener { hide() }

    }

    fun show(scope: CoroutineScope) {
        startTimestamp = Date().time
        binding.recordButtonOverlayImageView.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_microphone, context.theme))
        binding.inputBarCancelButton.alpha = 0.0f
        binding.inputBarMiddleContentContainer.alpha = 1.0f
        binding.lockView.alpha = 1.0f
        isVisible = true
        alpha = 0.0f
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 0.0f, 1.0f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            alpha = animator.animatedValue as Float
        }
        animation.start()
        animateDotView()
        pulse()
        animateLockViewUp()
        startTimer(scope)
    }

    fun hide() {
        alpha = 1.0f
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 1.0f, 0.0f)
        animation.duration = ShowHideDurationMS
        animation.addUpdateListener { animator ->
            alpha = animator.animatedValue as Float
            if (animator.animatedFraction == 1.0f) {
                isVisible = false
                dotViewAnimation?.repeatCount = 0
                pulseAnimation?.removeAllUpdateListeners()
            }
        }
        animation.start()
        delegate?.handleVoiceMessageUIHidden()
        stopTimer()
    }

    private fun startTimer(scope: CoroutineScope) {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                val duration = (Date().time - startTimestamp) / 1000L
                binding.recordingViewDurationTextView.text = DateUtils.formatElapsedTime(duration)

                delay(500)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun animateDotView() {
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 1.0f, 0.0f)
        dotViewAnimation = animation
        animation.duration = DotAnimationDurationMS
        animation.addUpdateListener { animator ->
            binding.dotView.alpha = animator.animatedValue as Float
        }
        animation.repeatCount = ValueAnimator.INFINITE
        animation.repeatMode = ValueAnimator.REVERSE
        animation.start()
    }

    private fun pulse() {
        val collapsedSize = toPx(80.0f, resources)
        val expandedSize = toPx(104.0f, resources)
        binding.pulseView.animateSizeChange(collapsedSize, expandedSize, 1000)
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 0.5, 0.0f)
        pulseAnimation = animation
        animation.duration = DotPulseAnimationDurationMS
        animation.addUpdateListener { animator ->
            binding.pulseView.alpha = animator.animatedValue as Float
            if (animator.animatedFraction == 1.0f && isVisible) { pulse() }
        }
        animation.start()
    }

    private fun animateLockViewUp() {
        val startMarginBottom = toPx(32, resources)
        val endMarginBottom = toPx(72, resources)
        val layoutParams = binding.lockView.layoutParams as LayoutParams
        layoutParams.bottomMargin = startMarginBottom
        binding.lockView.layoutParams = layoutParams
        val animation = ValueAnimator.ofObject(IntEvaluator(), startMarginBottom, endMarginBottom)
        animation.duration = AnimateLockDurationMS
        animation.addUpdateListener { animator ->
            layoutParams.bottomMargin = animator.animatedValue as Int
            binding.lockView.layoutParams = layoutParams
        }
        animation.start()
    }

    fun lock() {
        val fadeOutAnimation = ValueAnimator.ofObject(FloatEvaluator(), 1.0f, 0.0f)
        fadeOutAnimation.duration = AnimateLockDurationMS
        fadeOutAnimation.addUpdateListener { animator ->
            binding.inputBarMiddleContentContainer.alpha = animator.animatedValue as Float
            binding.lockView.alpha = animator.animatedValue as Float
        }
        fadeOutAnimation.start()
        val fadeInAnimation = ValueAnimator.ofObject(FloatEvaluator(), 0.0f, 1.0f)
        fadeInAnimation.duration = AnimateLockDurationMS
        fadeInAnimation.addUpdateListener { animator ->
            binding.inputBarCancelButton.alpha = animator.animatedValue as Float
        }
        fadeInAnimation.start()
        binding.recordButtonOverlayImageView.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_arrow_up, context.theme))
        binding.recordButtonOverlay.setOnClickListener { delegate?.sendVoiceMessage() }
        binding.inputBarCancelButton.setOnClickListener { delegate?.cancelVoiceMessage() }
    }
}

interface InputBarRecordingViewDelegate {

    fun handleVoiceMessageUIHidden()
    fun sendVoiceMessage()
    fun cancelVoiceMessage()
}
