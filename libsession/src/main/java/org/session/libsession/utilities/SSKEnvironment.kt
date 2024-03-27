package org.session.libsession.utilities

import android.content.Context
import android.util.Log
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.control.ClosedGroupControlMessage
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.sending_receiving.notifications.MessageNotifier
import org.session.libsession.snode.SnodeAPI.nowWithOffset
import org.session.libsession.utilities.recipients.Recipient

class SSKEnvironment(
    val typingIndicators: TypingIndicatorsProtocol,
    val readReceiptManager: ReadReceiptManagerProtocol,
    val profileManager: ProfileManagerProtocol,
    val notificationManager: MessageNotifier,
    val messageExpirationManager: MessageExpirationManagerProtocol
) {

    interface TypingIndicatorsProtocol {
        fun didReceiveTypingStartedMessage(context: Context, threadId: Long, author: Address, device: Int)
        fun didReceiveTypingStoppedMessage(context: Context, threadId: Long, author: Address, device: Int, isReplacedByIncomingMessage: Boolean)
        fun didReceiveIncomingMessage(context: Context, threadId: Long, author: Address, device: Int)
    }

    interface ReadReceiptManagerProtocol {
        fun processReadReceipts(context: Context, fromRecipientId: String, sentTimestamps: List<Long>, readTimestamp: Long)
    }

    interface ProfileManagerProtocol {
        companion object {
            const val NAME_PADDED_LENGTH = 64
        }

        fun setNickname(context: Context, recipient: Recipient, nickname: String?)
        fun setName(context: Context, recipient: Recipient, name: String?)
        fun setProfilePicture(context: Context, recipient: Recipient, profilePictureURL: String?, profileKey: ByteArray?)
        fun setUnidentifiedAccessMode(context: Context, recipient: Recipient, unidentifiedAccessMode: Recipient.UnidentifiedAccessMode)
        fun contactUpdatedInternal(contact: Contact): String?
    }

    interface MessageExpirationManagerProtocol {
        fun insertExpirationTimerMessage(message: ExpirationTimerUpdate)
        fun startAnyExpiration(timestamp: Long, author: String, expireStartedAt: Long)

        fun maybeStartExpiration(message: Message, startDisappearAfterRead: Boolean = false) {
            if (message is ExpirationTimerUpdate && message.isGroup || message is ClosedGroupControlMessage) return

            maybeStartExpiration(
                message.sentTimestamp ?: return,
                message.sender ?: return,
                message.expiryMode,
                startDisappearAfterRead || message.isSenderSelf
            )
        }

        fun startDisappearAfterRead(timestamp: Long, sender: String) {
            startAnyExpiration(
                timestamp,
                sender,
                expireStartedAt = nowWithOffset.coerceAtLeast(timestamp + 1)
            )
        }

        fun maybeStartExpiration(timestamp: Long, sender: String, mode: ExpiryMode, startDisappearAfterRead: Boolean = false) {
            val expireStartedAt = when (mode) {
                is ExpiryMode.AfterSend -> timestamp
                is ExpiryMode.AfterRead -> if (startDisappearAfterRead) nowWithOffset.coerceAtLeast(timestamp + 1) else return
                else -> return
            }

            startAnyExpiration(timestamp, sender, expireStartedAt)
        }
    }

    companion object {
        lateinit var shared: SSKEnvironment

        fun configure(typingIndicators: TypingIndicatorsProtocol,
                      readReceiptManager: ReadReceiptManagerProtocol,
                      profileManager: ProfileManagerProtocol,
                      notificationManager: MessageNotifier,
                      messageExpirationManager: MessageExpirationManagerProtocol) {
            if (Companion::shared.isInitialized) { return }
            shared = SSKEnvironment(typingIndicators, readReceiptManager, profileManager, notificationManager, messageExpirationManager)
        }
    }
}
