package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewUntrustedAttachmentBinding
import org.session.libsession.utilities.StringSubstitutionConstants.FILE_TYPE_KEY
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.dialogs.DownloadDialog
import org.thoughtcrime.securesms.util.ActivityDispatcher

class UntrustedAttachmentView: LinearLayout {
    private val binding: ViewUntrustedAttachmentBinding by lazy { ViewUntrustedAttachmentBinding.bind(this) }
    enum class AttachmentType {
        AUDIO,
        DOCUMENT,
        MEDIA
    }

    // region Lifecycle
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // endregion

    // region Updating
    fun bind(attachmentType: AttachmentType, @ColorInt textColor: Int) {
        val (iconRes, stringRes) = when (attachmentType) {
            AttachmentType.AUDIO -> R.drawable.ic_microphone to R.string.audio
            AttachmentType.DOCUMENT -> R.drawable.ic_document_large_light to R.string.files
            AttachmentType.MEDIA -> R.drawable.ic_image_white_24dp to R.string.media
        }
        val iconDrawable = ContextCompat.getDrawable(context,iconRes)!!
        iconDrawable.mutate().setTint(textColor)

        val text = Phrase.from(context, R.string.attachmentsTapToDownload)
            .put(FILE_TYPE_KEY, context.getString(stringRes))
            .format()
        binding.untrustedAttachmentTitle.text = text

        binding.untrustedAttachmentIcon.setImageDrawable(iconDrawable)
        binding.untrustedAttachmentTitle.text = text
    }
    // endregion

    // region Interaction
    fun showTrustDialog(recipient: Recipient) {
        ActivityDispatcher.get(context)?.showDialog(DownloadDialog(recipient))
    }

}