package org.thoughtcrime.securesms.conversation.disappearingmessages

import android.content.Context
import com.squareup.phrase.Phrase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.SSKEnvironment.MessageExpirationManagerProtocol
import org.session.libsession.utilities.StringSubstitutionConstants.DISAPPEARING_MESSAGES_TYPE_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.TIME_LARGE_KEY
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.getExpirationTypeDisplayValue
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities

class DisappearingMessages @Inject constructor(
    @ApplicationContext private val context: Context,
    private val textSecurePreferences: TextSecurePreferences,
    private val messageExpirationManager: MessageExpirationManagerProtocol,
) {
    fun set(threadId: Long, address: Address, mode: ExpiryMode, isGroup: Boolean) {
        val expiryChangeTimestampMs = SnodeAPI.nowWithOffset
        MessagingModuleConfiguration.shared.storage.setExpirationConfiguration(ExpirationConfiguration(threadId, mode, expiryChangeTimestampMs))

        val message = ExpirationTimerUpdate(isGroup = isGroup).apply {
            expiryMode = mode
            sender = textSecurePreferences.getLocalNumber()
            isSenderSelf = true
            recipient = address.serialize()
            sentTimestamp = expiryChangeTimestampMs
        }

        messageExpirationManager.insertExpirationTimerMessage(message)
        MessageSender.send(message, address)
        ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(context)
    }

    fun showFollowSettingDialog(context: Context, message: MessageRecord) = context.showSessionDialog {
        title(R.string.disappearingMessagesFollowSetting)
        text(if (message.expiresIn == 0L) {
            context.getString(R.string.disappearingMessagesFollowSettingOff)
        } else {
            Phrase.from(context, R.string.disappearingMessagesFollowSettingOn)
                .put(TIME_LARGE_KEY, ExpirationUtil.getExpirationDisplayValue(context, message.expiresIn.milliseconds))
                .put(DISAPPEARING_MESSAGES_TYPE_KEY, context.getExpirationTypeDisplayValue(message.isNotDisappearAfterRead))
                .format().toString()
        })

        dangerButton(
                text = if (message.expiresIn == 0L) R.string.dialog_disappearing_messages_follow_setting_confirm else R.string.set,
                contentDescription = if (message.expiresIn == 0L) R.string.AccessibilityId_confirm else R.string.AccessibilityId_set_button
        ) {
            set(message.threadId, message.recipient.address, message.expiryMode, message.recipient.isClosedGroupRecipient)
        }
        cancelButton()
    }
}

val MessageRecord.expiryMode get() = if (expiresIn <= 0) ExpiryMode.NONE
    else if (expireStarted == timestamp) ExpiryMode.AfterSend(expiresIn / 1000)
    else ExpiryMode.AfterRead(expiresIn / 1000)
