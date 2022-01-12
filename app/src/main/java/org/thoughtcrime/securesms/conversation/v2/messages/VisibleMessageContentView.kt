package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.text.getSpans
import androidx.core.text.toSpannable
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.view_visible_message_content.view.*
import network.loki.messenger.R
import okhttp3.HttpUrl
import org.session.libsession.utilities.ThemeUtil
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.conversation.v2.ModalUrlBottomSheet
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionUtilities
import org.thoughtcrime.securesms.conversation.v2.utilities.ModalURLSpan
import org.thoughtcrime.securesms.conversation.v2.utilities.TextUtilities.getIntersectedModalSpans
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.SmsMessageRecord
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.SearchUtil
import org.thoughtcrime.securesms.util.SearchUtil.StyleFactory
import org.thoughtcrime.securesms.util.UiModeUtilities
import org.thoughtcrime.securesms.util.getColorWithID
import org.thoughtcrime.securesms.util.toPx
import java.util.*
import kotlin.math.roundToInt

class VisibleMessageContentView : LinearLayout {
    var onContentClick: MutableList<((event: MotionEvent) -> Unit)> = mutableListOf()
    var onContentDoubleTap: (() -> Unit)? = null
    var delegate: VisibleMessageContentViewDelegate? = null
    var indexInAdapter: Int = -1

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.view_visible_message_content, this)
    }
    // endregion

    // region Updating
    fun bind(message: MessageRecord, isStartOfMessageCluster: Boolean, isEndOfMessageCluster: Boolean,
        glide: GlideRequests, maxWidth: Int, thread: Recipient, searchQuery: String?, contactIsTrusted: Boolean) {
        // Background
        val background = getBackground(message.isOutgoing, isStartOfMessageCluster, isEndOfMessageCluster)
        val colorID = if (message.isOutgoing) R.attr.message_sent_background_color else R.attr.message_received_background_color
        val color = ThemeUtil.getThemedColor(context, colorID)
        val filter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(color, BlendModeCompat.SRC_IN)
        background.colorFilter = filter
        setBackground(background)

        val onlyBodyMessage = message is SmsMessageRecord
        val mediaThumbnailMessage = contactIsTrusted && message is MmsMessageRecord && message.slideDeck.thumbnailSlide != null

        // reset visibilities / containers
        onContentClick.clear()
        albumThumbnailView.clearViews()
        onContentDoubleTap = null

        if (message.isDeleted) {
            deletedMessageView.isVisible = true
            deletedMessageView.bind(message, VisibleMessageContentView.getTextColor(context,message))
            return
        } else {
            deletedMessageView.isVisible = false
        }

        quoteView.isVisible = message is MmsMessageRecord && message.quote != null
        val quoteLayoutParams = quoteView.layoutParams
        quoteLayoutParams.width = if (mediaThumbnailMessage) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
        quoteView.layoutParams = quoteLayoutParams

        linkPreviewView.isVisible = message is MmsMessageRecord && message.linkPreviews.isNotEmpty()

        val linkPreviewLayout = linkPreviewView.layoutParams
        linkPreviewLayout.width = if (mediaThumbnailMessage) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
        linkPreviewView.layoutParams = linkPreviewLayout

        untrustedView.isVisible = !contactIsTrusted && message is MmsMessageRecord
        voiceMessageView.isVisible = contactIsTrusted && message is MmsMessageRecord && message.slideDeck.audioSlide != null
        documentView.isVisible = contactIsTrusted && message is MmsMessageRecord && message.slideDeck.documentSlide != null
        albumThumbnailView.isVisible = mediaThumbnailMessage
        openGroupInvitationView.isVisible = message.isOpenGroupInvitation

        var hideBody = false

        if (message is MmsMessageRecord && message.quote != null) {
            // TODO: test if this looks fine for deleted messages using message.isDeleted when unsend is activated
            quoteView.isVisible = true
            val quote = message.quote!!
            // The max content width is the max message bubble size - 2 times the horizontal padding - 2
            // times the horizontal margin. This unfortunately has to be calculated manually
            // here to get the layout right.
            val maxContentWidth = (maxWidth - 2 * resources.getDimension(R.dimen.medium_spacing) - 2 * toPx(16, resources)).roundToInt()
            val quoteText = if (quote.isOriginalMissing) {
                context.getString(R.string.QuoteView_original_missing)
            } else {
                quote.text
            }
            quoteView.bind(quote.author.toString(), quoteText, quote.attachment, thread,
                message.isOutgoing, maxContentWidth, message.isOpenGroupInvitation, message.threadId,
                quote.isOriginalMissing, glide)
            onContentClick.add { event ->
                val r = Rect()
                quoteView.getGlobalVisibleRect(r)
                if (r.contains(event.rawX.roundToInt(), event.rawY.roundToInt())) {
                    delegate?.scrollToMessageIfPossible(quote.id)
                }
            }
        }

        if (message is MmsMessageRecord && message.linkPreviews.isNotEmpty()) {
            linkPreviewView.bind(message, glide, isStartOfMessageCluster, isEndOfMessageCluster)
            onContentClick.add { event -> linkPreviewView.calculateHit(event) }
            // Body text view is inside the link preview for layout convenience
        } else if (message is MmsMessageRecord && message.slideDeck.audioSlide != null) {
            hideBody = true
            // Audio attachment
            if (contactIsTrusted || message.isOutgoing) {
                voiceMessageView.indexInAdapter = indexInAdapter
                voiceMessageView.delegate = context as? ConversationActivityV2
                voiceMessageView.bind(message, isStartOfMessageCluster, isEndOfMessageCluster)
                // We have to use onContentClick (rather than a click listener directly on the voice
                // message view) so as to not interfere with all the other gestures.
                onContentClick.add { voiceMessageView.togglePlayback() }
                onContentDoubleTap = { voiceMessageView.handleDoubleTap() }
            } else {
                // TODO: move this out to its own area
                untrustedView.bind(UntrustedAttachmentView.AttachmentType.AUDIO, VisibleMessageContentView.getTextColor(context,message))
                onContentClick.add { untrustedView.showTrustDialog(message.individualRecipient) }
            }
        } else if (message is MmsMessageRecord && message.slideDeck.documentSlide != null) {
            hideBody = true
            // Document attachment
            if (contactIsTrusted || message.isOutgoing) {
                documentView.bind(message, VisibleMessageContentView.getTextColor(context, message))
            } else {
                untrustedView.bind(UntrustedAttachmentView.AttachmentType.DOCUMENT, VisibleMessageContentView.getTextColor(context,message))
                onContentClick.add { untrustedView.showTrustDialog(message.individualRecipient) }
            }
        } else if (message is MmsMessageRecord && message.slideDeck.asAttachments().isNotEmpty()) {
            /*
             *    Images / Video attachment
             */
            if (contactIsTrusted || message.isOutgoing) {
                // isStart and isEnd of cluster needed for calculating the mask for full bubble image groups
                // bind after add view because views are inflated and calculated during bind
                albumThumbnailView.bind(
                        glideRequests = glide,
                        message = message,
                        isStart = isStartOfMessageCluster,
                        isEnd = isEndOfMessageCluster
                )
                onContentClick.add { event ->
                    albumThumbnailView.calculateHitObject(event, message, thread)
                }
            } else {
                untrustedView.bind(UntrustedAttachmentView.AttachmentType.MEDIA, VisibleMessageContentView.getTextColor(context,message))
                onContentClick.add { untrustedView.showTrustDialog(message.individualRecipient) }
            }
        } else if (message.isOpenGroupInvitation) {
            hideBody = true
            openGroupInvitationView.bind(message, VisibleMessageContentView.getTextColor(context, message))
            onContentClick.add { openGroupInvitationView.joinOpenGroup() }
        }

        bodyTextView.isVisible = message.body.isNotEmpty() && !hideBody

        // set it to use constraints if not only a text message, otherwise wrap content to whatever width it wants
        val params = bodyTextView.layoutParams
        params.width = if (onlyBodyMessage) ViewGroup.LayoutParams.WRAP_CONTENT else 0
        bodyTextView.layoutParams = params

        if (message.body.isNotEmpty() && !hideBody) {
            val color = getTextColor(context, message)
            bodyTextView.setTextColor(color)
            bodyTextView.setLinkTextColor(color)
            val body = getBodySpans(context, message, searchQuery)
            bodyTextView.text = body
            onContentClick.add { e: MotionEvent ->
                bodyTextView.getIntersectedModalSpans(e).forEach { span ->
                    span.onClick(bodyTextView)
                }
            }
        }
    }

    private fun getBackground(isOutgoing: Boolean, isStartOfMessageCluster: Boolean, isEndOfMessageCluster: Boolean): Drawable {
        val isSingleMessage = (isStartOfMessageCluster && isEndOfMessageCluster)
        @DrawableRes val backgroundID: Int
        if (isSingleMessage) {
            backgroundID = if (isOutgoing) R.drawable.message_bubble_background_sent_alone else R.drawable.message_bubble_background_received_alone
        } else if (isStartOfMessageCluster) {
            backgroundID = if (isOutgoing) R.drawable.message_bubble_background_sent_start else R.drawable.message_bubble_background_received_start
        } else if (isEndOfMessageCluster) {
            backgroundID = if (isOutgoing) R.drawable.message_bubble_background_sent_end else R.drawable.message_bubble_background_received_end
        } else {
            backgroundID = if (isOutgoing) R.drawable.message_bubble_background_sent_middle else R.drawable.message_bubble_background_received_middle
        }
        return ResourcesCompat.getDrawable(resources, backgroundID, context.theme)!!
    }

    fun recycle() {
        arrayOf(
            deletedMessageView,
            untrustedView,
            voiceMessageView,
            openGroupInvitationView,
            documentView,
            quoteView,
            linkPreviewView,
            albumThumbnailView,
            bodyTextView
        ).forEach { view -> view.isVisible = false }
    }
    // endregion

    // region Convenience
    companion object {

        fun getBodySpans(context: Context, message: MessageRecord, searchQuery: String?): Spannable {
            var body = message.body.toSpannable()

            body = MentionUtilities.highlightMentions(body, message.isOutgoing, message.threadId, context)
            body = SearchUtil.getHighlightedSpan(Locale.getDefault(), StyleFactory { BackgroundColorSpan(Color.WHITE) }, body, searchQuery)
            body = SearchUtil.getHighlightedSpan(Locale.getDefault(), StyleFactory { ForegroundColorSpan(Color.BLACK) }, body, searchQuery)

            Linkify.addLinks(body, Linkify.WEB_URLS)

            // replace URLSpans with ModalURLSpans
            body.getSpans<URLSpan>(0, body.length).toList().forEach { urlSpan ->
                val updatedUrl = urlSpan.url.let { HttpUrl.parse(it).toString() }
                val replacementSpan = ModalURLSpan(updatedUrl) { url ->
                    val activity = context as AppCompatActivity
                    ModalUrlBottomSheet(url).show(activity.supportFragmentManager, "Open URL Dialog")
                }
                val start = body.getSpanStart(urlSpan)
                val end = body.getSpanEnd(urlSpan)
                val flags = body.getSpanFlags(urlSpan)
                body.removeSpan(urlSpan)
                body.setSpan(replacementSpan, start, end, flags)
            }
            return body
        }

        @ColorInt
        fun getTextColor(context: Context, message: MessageRecord): Int {
            val isDayUiMode = UiModeUtilities.isDayUiMode(context)
            val colorID = if (message.isOutgoing) {
                if (isDayUiMode) R.color.white else R.color.black
            } else {
                if (isDayUiMode) R.color.black else R.color.white
            }
            return context.resources.getColorWithID(colorID, context.theme)
        }
    }
    // endregion
}

interface VisibleMessageContentViewDelegate {

    fun scrollToMessageIfPossible(timestamp: Long)
}