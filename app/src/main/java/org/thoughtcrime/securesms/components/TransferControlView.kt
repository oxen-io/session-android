package org.thoughtcrime.securesms.components

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.annimon.stream.Stream
import com.pnikosis.materialishprogress.ProgressWheel
import kotlin.math.max
import network.loki.messenger.R
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.session.libsession.messaging.sending_receiving.attachments.Attachment
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentTransferProgress
import org.session.libsession.utilities.StringSubstitutionConstants.COUNT_KEY
import org.session.libsession.utilities.ViewUtil
import org.thoughtcrime.securesms.events.PartProgressEvent
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.ui.getSubbedString

class TransferControlView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context!!, attrs, defStyleAttr) {
    private var slides: List<Slide>? = null
    private var current: View? = null

    private val progressWheel: ProgressWheel
    private val downloadDetails: View
    private val downloadDetailsText: TextView
    private val downloadProgress: MutableMap<Attachment, Float>

    init {
        inflate(context, R.layout.transfer_controls_view, this)

        isLongClickable = false
        ViewUtil.setBackground(this, ContextCompat.getDrawable(context!!, R.drawable.transfer_controls_background))
        visibility = GONE
        layoutTransition = LayoutTransition()

        this.downloadProgress = HashMap()
        this.progressWheel = ViewUtil.findById(this, R.id.progress_wheel)
        this.downloadDetails = ViewUtil.findById(this, R.id.download_details)
        this.downloadDetailsText = ViewUtil.findById(this, R.id.download_details_text)
    }

    override fun setFocusable(focusable: Boolean) {
        super.setFocusable(focusable)
        downloadDetails.isFocusable = focusable
    }

    override fun setClickable(clickable: Boolean) {
        super.setClickable(clickable)
        downloadDetails.isClickable = clickable
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        EventBus.getDefault().unregister(this)
    }

    private fun setSlides(slides: List<Slide>) {
        require(slides.isNotEmpty()) { "Must provide at least one slide." }

        this.slides = slides

        if (!isUpdateToExistingSet(slides)) {
            downloadProgress.clear()
            Stream.of(slides).forEach { s: Slide -> downloadProgress[s.asAttachment()] = 0f }
        }

        for (slide in slides) {
            if (slide.asAttachment().transferState == AttachmentTransferProgress.TRANSFER_PROGRESS_DONE) {
                downloadProgress[slide.asAttachment()] = 1f
            }
        }

        when (getTransferState(slides)) {
            AttachmentTransferProgress.TRANSFER_PROGRESS_STARTED -> showProgressSpinner(calculateProgress(downloadProgress))
            AttachmentTransferProgress.TRANSFER_PROGRESS_PENDING, AttachmentTransferProgress.TRANSFER_PROGRESS_FAILED -> {
                downloadDetailsText.text = getDownloadText(this.slides!!)
                display(downloadDetails)
            }

            else -> display(null)
        }
    }

    @JvmOverloads
    fun showProgressSpinner(progress: Float = calculateProgress(downloadProgress)) {
        if (progress == 0f) {
            progressWheel.spin()
        } else {
            progressWheel.setInstantProgress(progress)
        }
        display(progressWheel)
    }

    fun clear() {
        clearAnimation()
        visibility = GONE
        if (current != null) {
            current!!.clearAnimation()
            current!!.visibility = GONE
        }
        current = null
        slides = null
    }

    private fun isUpdateToExistingSet(slides: List<Slide>): Boolean {
        if (slides.size != downloadProgress.size) {
            return false
        }

        for (slide in slides) {
            if (!downloadProgress.containsKey(slide.asAttachment())) {
                return false
            }
        }

        return true
    }

    private fun getTransferState(slides: List<Slide>): Int {
        var transferState = AttachmentTransferProgress.TRANSFER_PROGRESS_DONE
        for (slide in slides) {
            transferState = if (slide.transferState == AttachmentTransferProgress.TRANSFER_PROGRESS_PENDING && transferState == AttachmentTransferProgress.TRANSFER_PROGRESS_DONE) {
                slide.transferState
            } else {
                max(transferState.toDouble(), slide.transferState.toDouble()).toInt()
            }
        }
        return transferState
    }

    private fun getDownloadText(slides: List<Slide>): String {
        if (slides.size == 1) {
            return slides[0].contentDescription
        } else {
            val downloadCount = Stream.of(slides).reduce(0) { count: Int, slide: Slide ->
                if (slide.transferState != AttachmentTransferProgress.TRANSFER_PROGRESS_DONE) count + 1 else count
            }
            return context.getSubbedString(R.string.andMore, COUNT_KEY to downloadCount.toString())
        }
    }

    private fun display(view: View?) {
        if (current != null) {
            current!!.visibility = GONE
        }

        if (view != null) {
            view.visibility = VISIBLE
        } else {
            visibility = GONE
        }

        current = view
    }

    private fun calculateProgress(downloadProgress: Map<Attachment, Float>): Float {
        var totalProgress = 0f
        for (progress in downloadProgress.values) {
            totalProgress += progress / downloadProgress.size
        }
        return totalProgress
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventAsync(event: PartProgressEvent) {
        if (downloadProgress.containsKey(event.attachment)) {
            downloadProgress[event.attachment] = event.progress.toFloat() / event.total
            progressWheel.setInstantProgress(calculateProgress(downloadProgress))
        }
    }
}
