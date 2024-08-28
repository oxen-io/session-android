package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.bumptech.glide.RequestManager
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewLinkPreviewBinding
import org.session.libsession.utilities.getColorFromAttr
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.components.CornerMask
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.conversation.v2.utilities.MessageBubbleUtilities
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.ImageSlide

class LinkPreviewView : LinearLayout {
    private val binding: ViewLinkPreviewBinding by lazy { ViewLinkPreviewBinding.bind(this) }
    private val cornerMask by lazy { CornerMask(this) }
    private var url: String? = null

    // region Lifecycle
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    // endregion

    // region Updating
    fun bind(
        message: MmsMessageRecord,
        glide: RequestManager,
        isStartOfMessageCluster: Boolean,
        isEndOfMessageCluster: Boolean
    ) {
        val linkPreview = message.linkPreviews.first()
        url = linkPreview.url
        // Thumbnail
        if (linkPreview.getThumbnail().isPresent) {
            // This internally fetches the thumbnail
            binding.thumbnailImageView.root.setImageResource(glide, ImageSlide(context, linkPreview.getThumbnail().get()), isPreview = false)
            binding.thumbnailImageView.root.loadIndicator.isVisible = false
        }
        // Title
        binding.titleTextView.text = linkPreview.title
        val textColorID = if (message.isOutgoing) {
            R.attr.message_sent_text_color
        } else {
            R.attr.message_received_text_color
        }
        binding.titleTextView.setTextColor(context.getColorFromAttr(textColorID))
        // Body
        // Corner radii
        val cornerRadii = MessageBubbleUtilities.calculateRadii(context, isStartOfMessageCluster, isEndOfMessageCluster, message.isOutgoing)
        cornerMask.setTopLeftRadius(cornerRadii[0])
        cornerMask.setTopRightRadius(cornerRadii[1])

        // Only round the bottom corners if there is no body text
        if (message.body.isEmpty()) {
            cornerMask.setBottomRightRadius(cornerRadii[2])
            cornerMask.setBottomLeftRadius(cornerRadii[3])
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        cornerMask.mask(canvas)
    }
    // endregion

    // region Interaction
    fun calculateHit(event: MotionEvent) {
        val rawXInt = event.rawX.toInt()
        val rawYInt = event.rawY.toInt()
        val hitRect = Rect(rawXInt, rawYInt, rawXInt, rawYInt)
        val previewRect = Rect()
        binding.mainLinkPreviewContainer.getGlobalVisibleRect(previewRect)
        if (previewRect.contains(hitRect)) {
            openURL()
            return
        }
    }

    // Method to show the open or copy URL dialog
    private fun openURL() {
        val url = this.url ?: return Log.w("LinkPreviewView", "Cannot open a null URL")
        val activity = context as? ConversationActivityV2
        activity?.showOpenUrlDialog(url)
    }
    // endregion
}