package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import network.loki.messenger.databinding.ViewDocumentBinding
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

        // Show the progress spinner if the attachment is downloading, otherwise show
        // the document icon (and always remove the other, whichever one that is)
        binding.documentViewProgress.isVisible = message.isMediaPending
        binding.documentViewIconImageView.isVisible = !message.isMediaPending
    }
    // endregion

}