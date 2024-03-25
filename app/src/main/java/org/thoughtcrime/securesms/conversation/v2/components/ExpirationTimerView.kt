package org.thoughtcrime.securesms.conversation.v2.components

import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import network.loki.messenger.R
import org.session.libsession.snode.SnodeAPI.nowWithOffset
import kotlin.math.round

class ExpirationTimerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {
    private val frames = intArrayOf(
        R.drawable.timer00,
        R.drawable.timer05,
        R.drawable.timer10,
        R.drawable.timer15,
        R.drawable.timer20,
        R.drawable.timer25,
        R.drawable.timer30,
        R.drawable.timer35,
        R.drawable.timer40,
        R.drawable.timer45,
        R.drawable.timer50,
        R.drawable.timer55,
        R.drawable.timer60
    )

    fun setTimerIcon() {
        setExpirationTime(0L, 0L)
    }

    fun setExpirationTime(startedAt: Long, expiresIn: Long) {
        if (expiresIn == 0L) {
            setImageResource(R.drawable.timer55)
            return
        }

        if (startedAt == 0L) {
            // timer has not started
            setImageResource(R.drawable.timer60)
            return
        }

        val elapsedTime = nowWithOffset - startedAt
        val remainingTime = expiresIn - elapsedTime
        val remainingPercent = (remainingTime / expiresIn.toFloat()).coerceIn(0f, 1f)

        val frameCount = round(frames.size * remainingPercent).toInt().coerceIn(1, frames.size)
        val frameTime = round(remainingTime / frameCount.toFloat()).toInt()

        AnimationDrawable().apply {
            frames.take(frameCount).reversed().forEach { addFrame(ContextCompat.getDrawable(context, it)!!, frameTime) }
            isOneShot = true
        }.also(::setImageDrawable).apply(AnimationDrawable::start)
    }
}
