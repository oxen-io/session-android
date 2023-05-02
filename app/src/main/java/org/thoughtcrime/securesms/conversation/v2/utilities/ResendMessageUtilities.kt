package org.thoughtcrime.securesms.conversation.v2.utilities

import android.content.Context
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.visible.LinkPreview
import org.session.libsession.messaging.messages.visible.OpenGroupInvitation
import org.session.libsession.messaging.messages.visible.Quote
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.utilities.UpdateMessageData
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord

object ResendMessageUtilities {
    fun resend(context: Context, messageRecord: MessageRecord, userBlindedKey: String?, isSync: Boolean) {
        val recipient: Recipient = messageRecord.recipient
        val message = VisibleMessage()
        message.id = messageRecord.getId()
        if (messageRecord.isOpenGroupInvitation) {
            message.openGroupInvitation = OpenGroupInvitation()
                .apply {
                    UpdateMessageData.fromJSON(messageRecord.body)
                        ?.let { it.kind as? UpdateMessageData.Kind.OpenGroupInvitation }
                        ?.let {
                            name = it.groupName
                            url = it.groupUrl
                        }
                }
        } else {
            message.text = messageRecord.body
        }
        message.sentTimestamp = messageRecord.timestamp
        if (recipient.isGroupRecipient) {
            message.groupPublicKey = recipient.address.toGroupString()
        } else {
            message.recipient = messageRecord.recipient.address.serialize()
        }
        message.threadID = messageRecord.threadId
        if (messageRecord.isMms) {
            val mmsMessageRecord = messageRecord as MmsMessageRecord
            if (mmsMessageRecord.linkPreviews.isNotEmpty()) {
                message.linkPreview = LinkPreview.from(mmsMessageRecord.linkPreviews[0])
            }
            if (mmsMessageRecord.quote != null) {
                message.quote = Quote.from(mmsMessageRecord.quote!!.quoteModel)
                    ?.apply {
                        if (userBlindedKey != null && messageRecord.quote!!.author.serialize() == TextSecurePreferences.getLocalNumber(context)) {
                            publicKey = userBlindedKey
                        }
                    }
            }
            message.addSignalAttachments(mmsMessageRecord.slideDeck.asAttachments())
        }
        val publicKey = MessagingModuleConfiguration.shared.storage.getUserPublicKey() ?: return
        if (isSync) {
            MessagingModuleConfiguration.shared.storage.markAsResyncing(messageRecord.timestamp, publicKey)
        } else {
            MessagingModuleConfiguration.shared.storage.markAsSending(messageRecord.timestamp, publicKey)
        }

        val toSendAddress = when {
            messageRecord.isFailed -> recipient.address
            messageRecord.isSyncFailed -> Address.fromSerialized(TextSecurePreferences.getLocalNumber(context)!!)
            else -> throw Exception("uhh this shouldn't happen")
        }
        MessageSender.send(message, toSendAddress)
    }
}
