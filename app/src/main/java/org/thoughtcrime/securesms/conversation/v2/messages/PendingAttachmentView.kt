package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewPendingAttachmentBinding
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.dialogs.AutoDownloadDialog
import org.thoughtcrime.securesms.util.ActivityDispatcher
import org.thoughtcrime.securesms.util.createAndStartAttachmentDownload
import org.thoughtcrime.securesms.util.displaySize
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class PendingAttachmentView: LinearLayout {
    private val binding by lazy { ViewPendingAttachmentBinding.bind(this) }
    enum class AttachmentType {
        AUDIO,
        DOCUMENT,
        IMAGE,
        VIDEO,
    }

    // region Lifecycle
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // endregion
    @Inject lateinit var storage: StorageProtocol

    // region Updating
    fun bind(attachmentType: AttachmentType, @ColorInt textColor: Int, attachment: DatabaseAttachment) {
        val stringRes = when (attachmentType) {
            AttachmentType.AUDIO -> R.string.Slide_audio
            AttachmentType.DOCUMENT -> R.string.document
            AttachmentType.IMAGE -> R.string.image
            AttachmentType.VIDEO -> R.string.video
        }

        val text = context.getString(R.string.UntrustedAttachmentView_download_attachment, context.getString(stringRes).lowercase(Locale.ROOT))

        binding.pendingDownloadIcon.setColorFilter(textColor)
        binding.pendingDownloadSize.text = attachment.displaySize()
        binding.pendingDownloadTitle.text = text
    }
    // endregion

    // region Interaction
    fun showDownloadDialog(threadRecipient: Recipient, attachment: DatabaseAttachment) {
        if (!storage.shouldAutoDownloadAttachments(threadRecipient)) {
            // just download
            ActivityDispatcher.get(context)?.showDialog(AutoDownloadDialog(threadRecipient, attachment))
        }
    }
}