package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewControlMessageBinding
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.thoughtcrime.securesms.conversation.disappearingmessages.DisappearingMessages
import org.thoughtcrime.securesms.conversation.disappearingmessages.expiryMode
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import javax.inject.Inject

@AndroidEntryPoint
class ControlMessageView : LinearLayout {

    private val TAG = "ControlMessageView"

    private lateinit var binding: ViewControlMessageBinding

    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    @Inject lateinit var disappearingMessages: DisappearingMessages

    private fun initialize() {
        binding = ViewControlMessageBinding.inflate(LayoutInflater.from(context), this, true)
        layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
    }

    fun bind(message: MessageRecord, previous: MessageRecord?) {
        binding.dateBreakTextView.showDateBreak(message, previous)
        binding.iconImageView.isGone = true
        binding.expirationTimerView.isGone = true
        binding.followSetting.isGone = true
        var messageBody: CharSequence = message.getDisplayBody(context)
        binding.root.contentDescription = null
        binding.textView.text = messageBody
        when {
            message.isExpirationTimerUpdate -> {
                binding.apply {
                    expirationTimerView.isVisible = true

                    val threadRecipient = DatabaseComponent.get(context).threadDatabase().getRecipientForThreadId(message.threadId)

                    if (threadRecipient?.isClosedGroupRecipient == true) {
                        expirationTimerView.setTimerIcon()
                    } else {
                        expirationTimerView.setExpirationTime(message.expireStarted, message.expiresIn)
                    }

                    followSetting.isVisible = ExpirationConfiguration.isNewConfigEnabled
                        && !message.isOutgoing
                        && message.expiryMode != (MessagingModuleConfiguration.shared.storage.getExpirationConfiguration(message.threadId)?.expiryMode ?: ExpiryMode.NONE)
                        && threadRecipient?.isGroupRecipient != true

                    followSetting.setOnClickListener { disappearingMessages.showFollowSettingDialog(context, message) }
                }
            }
            message.isMediaSavedNotification -> {
                binding.iconImageView.apply {
                    setImageDrawable(
                        ResourcesCompat.getDrawable(resources, R.drawable.ic_file_download_white_36dp, context.theme)
                    )
                    isVisible = true
                }
            }
            message.isMessageRequestResponse -> {
                binding.textView.text =  context.getString(R.string.message_requests_accepted)
                binding.root.contentDescription=context.getString(R.string.AccessibilityId_message_request_config_message)
            }
            message.isCallLog -> {
                val drawable = when {
                    message.isIncomingCall -> R.drawable.ic_incoming_call
                    message.isOutgoingCall -> R.drawable.ic_outgoing_call
                    message.isFirstMissedCall -> R.drawable.ic_info_outline_light
                    else -> R.drawable.ic_missed_call
                }
                binding.textView.isVisible = false
                binding.callTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(ResourcesCompat.getDrawable(resources, drawable, context.theme), null, null, null)
                binding.callTextView.text = messageBody

                if (message.expireStarted > 0 && message.expiresIn > 0) {
                    binding.expirationTimerView.isVisible = true
                    binding.expirationTimerView.setExpirationTime(message.expireStarted, message.expiresIn)
                }
            }
        }

        binding.textView.isGone = message.isCallLog
        binding.callView.isVisible = message.isCallLog
    }

    fun recycle() {

    }
}