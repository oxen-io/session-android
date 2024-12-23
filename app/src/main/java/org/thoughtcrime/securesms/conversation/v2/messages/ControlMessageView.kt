package org.thoughtcrime.securesms.conversation.v2.messages

import android.Manifest
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewControlMessageBinding
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.getColorFromAttr
import org.thoughtcrime.securesms.conversation.disappearingmessages.DisappearingMessages
import org.thoughtcrime.securesms.conversation.disappearingmessages.expiryMode
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.preferences.PrivacySettingsActivity
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.ui.findActivity
import org.thoughtcrime.securesms.ui.getSubbedCharSequence
import org.thoughtcrime.securesms.ui.getSubbedString
import javax.inject.Inject


@AndroidEntryPoint
class ControlMessageView : LinearLayout {

    private val TAG = "ControlMessageView"

    private val binding = ViewControlMessageBinding.inflate(LayoutInflater.from(context), this, true)

    private val infoDrawable by lazy {
        val d = ResourcesCompat.getDrawable(resources, R.drawable.ic_info_outline_white_24dp, context.theme)
        d?.setTint(context.getColorFromAttr(R.attr.message_received_text_color))
        d
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @Inject lateinit var disappearingMessages: DisappearingMessages

    val controlContentView: View get() = binding.controlContentView

    init {
        layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
    }

    fun bind(message: MessageRecord, previous: MessageRecord?, longPress: (() -> Unit)? = null) {
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

                    if (followSetting.isVisible) {
                        binding.controlContentView.setOnClickListener { disappearingMessages.showFollowSettingDialog(context, message) }
                    } else {
                        binding.controlContentView.setOnClickListener(null)
                    }
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
                val msgRecipient = message.recipient.address.serialize()
                val me = TextSecurePreferences.getLocalNumber(context)
                binding.textView.text =  if(me == msgRecipient) { // you accepted the user's request
                    val threadRecipient = DatabaseComponent.get(context).threadDatabase().getRecipientForThreadId(message.threadId)
                    context.getSubbedCharSequence(
                        R.string.messageRequestYouHaveAccepted,
                        NAME_KEY to (threadRecipient?.name ?: "")
                    )
                } else { // they accepted your request
                    context.getString(R.string.messageRequestsAccepted)
                }

                binding.root.contentDescription = context.getString(R.string.AccessibilityId_message_request_config_message)
            }
            message.isCallLog -> {
                val drawable = when {
                    message.isIncomingCall -> R.drawable.ic_incoming_call
                    message.isOutgoingCall -> R.drawable.ic_outgoing_call
                    else -> R.drawable.ic_missed_call
                }
                binding.textView.isVisible = false
                binding.callTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    ResourcesCompat.getDrawable(resources, drawable, context.theme),
                    null, null, null)
                binding.callTextView.text = messageBody

                if (message.expireStarted > 0 && message.expiresIn > 0) {
                    binding.expirationTimerView.isVisible = true
                    binding.expirationTimerView.setExpirationTime(message.expireStarted, message.expiresIn)
                }

                // remove clicks by default
                binding.controlContentView.setOnClickListener(null)
                hideInfo()

                // handle click behaviour depending on criteria
                if (message.isMissedCall || message.isFirstMissedCall) {
                    when {
                        // when the call toggle is disabled in the privacy screen,
                        // show a dedicated privacy dialog
                        !TextSecurePreferences.isCallNotificationsEnabled(context) -> {
                            showInfo()
                            binding.controlContentView.setOnClickListener {
                                context.showSessionDialog {
                                    val titleTxt = context.getSubbedString(
                                        R.string.callsMissedCallFrom,
                                        NAME_KEY to message.individualRecipient.name!!
                                    )
                                    title(titleTxt)

                                    val bodyTxt = context.getSubbedCharSequence(
                                        R.string.callsYouMissedCallPermissions,
                                        NAME_KEY to message.individualRecipient.name!!
                                    )
                                    text(bodyTxt)

                                    button(R.string.sessionSettings) {
                                        Intent(context, PrivacySettingsActivity::class.java)
                                            .let(context::startActivity)
                                    }
                                    cancelButton()
                                }
                            }
                        }

                        // if we're currently missing the audio/microphone permission,
                        // show a dedicated permission dialog
                        !Permissions.hasAll(context, Manifest.permission.RECORD_AUDIO) -> {
                            showInfo()
                            binding.controlContentView.setOnClickListener {
                                context.showSessionDialog {
                                    val titleTxt = context.getSubbedString(
                                        R.string.callsMissedCallFrom,
                                        NAME_KEY to message.individualRecipient.name!!
                                    )
                                    title(titleTxt)

                                    val bodyTxt = context.getSubbedCharSequence(
                                        R.string.callsMicrophonePermissionsRequired,
                                        NAME_KEY to message.individualRecipient.name!!
                                    )
                                    text(bodyTxt)

                                    button(R.string.theContinue) {
                                        Permissions.with(context.findActivity())
                                            .request(Manifest.permission.RECORD_AUDIO)
                                            .withPermanentDenialDialog(
                                                context.getSubbedString(R.string.permissionsMicrophoneAccessRequired,
                                                    APP_NAME_KEY to context.getString(R.string.app_name))
                                            )
                                            .execute()
                                    }
                                    cancelButton()
                                }
                            }
                        }
                    }
                }
            }
        }

        binding.textView.isGone = message.isCallLog
        binding.callView.isVisible = message.isCallLog

        // handle long clicked if it was passed on
        longPress?.let {
            binding.controlContentView.setOnLongClickListener {
                longPress.invoke()
                true
            }
        }
    }

    fun showInfo(){
        binding.callTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
            binding.callTextView.compoundDrawablesRelative.first(),
            null,
            infoDrawable,
            null
        )
    }

    fun hideInfo(){
        binding.callTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
            binding.callTextView.compoundDrawablesRelative.first(),
            null,
            null,
            null
        )
    }

    fun recycle() {

    }
}