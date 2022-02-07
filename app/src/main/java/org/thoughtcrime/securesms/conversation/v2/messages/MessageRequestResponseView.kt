package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewMessageRequestResponseBinding
import org.thoughtcrime.securesms.database.model.MessageRecord

class MessageRequestResponseView : LinearLayout {
    private lateinit var binding: ViewMessageRequestResponseBinding
    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        binding = ViewMessageRequestResponseBinding.inflate(LayoutInflater.from(context), this, true)
    }
    // endregion

    // region Updating
    fun bind(message: MessageRecord, @ColorInt textColor: Int) {
        assert(message.isMessageRequestResponse)
        binding.responseTitleTextView.text = context.getString(R.string.message_requests_accepted)
        binding.responseTitleTextView.setTextColor(textColor)
    }
    // endregion
}