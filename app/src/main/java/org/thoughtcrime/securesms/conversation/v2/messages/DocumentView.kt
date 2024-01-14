package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewDocumentBinding
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.database.model.MmsMessageRecord


class DocumentView : LinearLayout {
    private val binding: ViewDocumentBinding by lazy { ViewDocumentBinding.bind(this) }

    // region Lifecycle
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    // endregion

    // region Updating
    fun bind(message: MmsMessageRecord, @ColorInt textColor: Int) {
        val document = message.slideDeck.documentSlide!!
        binding.documentTitleTextView.text = document.fileName.or("Untitled File")
        binding.documentTitleTextView.setTextColor(textColor)
        binding.documentViewIconImageView.imageTintList = ColorStateList.valueOf(textColor)

        /*
        // Make the icon for the file appear as sending if attachment download is not yet complete
        // Note: The `!message.isRead/isDelivered` clause stops previous messages from changing icon
        // when we/ receive a new message with a non-scheme attachment (like a zip etc.). I have no
        // idea why other files can look as if they have media pending after they've downloaded, but
        // they can - although going out of the message thread and then back in shows the icons as they
        // should be.
        // TODO: Is this due to a race condition? Ask Harris what he thinks...
        if (message.isMediaPending && !message.isRead && !message.isDelivered) {
            Log.d("[ACL]", "[DocumentView] Setting `documentViewIconImageView` to look like status pending! 12345")

            // Create the animation
            var rotateAnimation = RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
            rotateAnimation.interpolator = LinearInterpolator()
            rotateAnimation.repeatCount = Animation.INFINITE
            rotateAnimation.duration = 1000 // Duration is in milliseconds, so 1000ms is 1 second

            // Set a custom animation listener on our rotate animation so when it stops (which
            // occurs when the attachment download has completed) we can change the icon back to the
            // file icon.
            val rotationAnimationListener = RotationAnimationListener(binding.documentViewIconImageView, binding.documentViewIconImageView.drawable)
            rotateAnimation.setAnimationListener(rotationAnimationListener)

            // Set the icon image and start the rotation to act like a spinner
            //binding.documentViewIconImageView.setImageResource(R.drawable.ic_delivery_status_sending)
            binding.documentViewIconImageView.setImageResource(R.drawable.ic_message_details__refresh)
            binding.documentViewIconImageView.startAnimation(rotateAnimation)
        }
        */
    }
    // endregion

    // Class to listen for the end of the download 'rotation' animation and set the document icon
    // back on the message's ImageView.
    class RotationAnimationListener(private val documentViewIconImageView: ImageView, private val originalDrawable: Drawable): AnimationListener {
        override fun onAnimationStart(animation: Animation?) { /* Do nothing */ }

        override fun onAnimationEnd(animation: Animation?) {
            Log.d("[ACL]", "Detected that animation ended - resetting document icon!")
            // Set the document icon back on the ImageView instead of the rotating icon
            // Note: `setImageResource` operates on the UI thread which apparently can cause a
            // "latency hiccup" so `setImageDrawable` is preferred.
            //documentViewIconImageView.setImageResource(R.drawable.ic_document_large_light) // No!
            documentViewIconImageView.setImageDrawable(originalDrawable)
        }

        override fun onAnimationRepeat(animation: Animation?) { /* Do nothing */ }
    }

}