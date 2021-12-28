package org.thoughtcrime.securesms.conversation.v2

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.control.UnsendRequest
import org.session.libsession.messaging.messages.signal.OutgoingTextMessage
import org.session.libsession.messaging.messages.visible.OpenGroupInvitation
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.DraftDatabase
import org.thoughtcrime.securesms.database.LokiMessageDatabase
import org.thoughtcrime.securesms.database.LokiThreadDatabase
import org.thoughtcrime.securesms.database.MmsDatabase
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.SmsDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.notifications.MarkReadReceiver

//FIXME: extend from ViewModel instead and remove android.* dependencies
class ConversationViewModel(
    val threadId: Long,
    private val threadDb: ThreadDatabase,
    private val draftDb: DraftDatabase,
    private val lokiThreadDb: LokiThreadDatabase,
    private val smsDb: SmsDatabase,
    private val mmsDb: MmsDatabase,
    private val recipientDb: RecipientDatabase,
    private val lokiMessageDb: LokiMessageDatabase,
    application: Application
) : AndroidViewModel(application) {

    val recipient: Recipient by lazy {
        threadDb.getRecipientForThreadId(threadId)!!
    }

    fun markAllAsRead() {
        val messages = threadDb.setRead(threadId, true)
        if (recipient.isGroupRecipient) {
            for (message in messages) {
                MarkReadReceiver.scheduleDeletion(getApplication(), message.expirationInfo)
            }
        } else {
            MarkReadReceiver.process(getApplication(), messages)
        }
        ApplicationContext.getInstance(getApplication()).messageNotifier.updateNotification(getApplication(), false, 0)
    }

    fun saveDraft(text: String) {
        if (text.isEmpty()) return
        val drafts = DraftDatabase.Drafts()
        drafts.add(DraftDatabase.Draft(DraftDatabase.Draft.TEXT, text))
        draftDb.insertDrafts(threadId, drafts)
    }

    fun getDraft(): String? {
        val drafts = draftDb.getDrafts(threadId)
        draftDb.clearDrafts(threadId)
        return drafts.find { it.type == DraftDatabase.Draft.TEXT }?.value
    }

    fun inviteContacts(contacts: Array<String>) {
        val openGroup = lokiThreadDb.getOpenGroupChat(threadId) ?: return
        for (contact in contacts) {
            val recipient = Recipient.from(getApplication(), Address.fromSerialized(contact), true)
            val message = VisibleMessage()
            message.sentTimestamp = System.currentTimeMillis()
            val openGroupInvitation = OpenGroupInvitation()
            openGroupInvitation.name = openGroup.name
            openGroupInvitation.url = openGroup.joinURL
            message.openGroupInvitation = openGroupInvitation
            val outgoingTextMessage = OutgoingTextMessage.fromOpenGroupInvitation(openGroupInvitation, recipient, message.sentTimestamp)
            smsDb.insertMessageOutbox(-1, outgoingTextMessage, message.sentTimestamp!!)
            MessageSender.send(message, recipient.address)
        }
    }

    fun unblock() {
        if (recipient.isContactRecipient) {
            recipientDb.setBlocked(recipient, false)
        }
    }
    fun deleteForEveryone(message: MessageRecord) {
        buildUnsendRequest(message)?.let { unsendRequest ->
            MessageSender.send(unsendRequest, recipient.address)
        }
        val messageDataProvider = MessagingModuleConfiguration.shared.messageDataProvider
        val openGroup = lokiThreadDb.getOpenGroupChat(threadId)
        if (openGroup != null) {
            lokiMessageDb.getServerID(message.id, !message.isMms)?.let { messageServerID ->
                OpenGroupAPIV2.deleteMessage(messageServerID, openGroup.room, openGroup.server)
                    .success {
                        messageDataProvider.deleteMessage(message.id, !message.isMms)
                    }.failUi { error ->
                        Toast.makeText(getApplication(), "Couldn't delete message due to error: $error", Toast.LENGTH_LONG).show()
                    }
            }
        } else {
            messageDataProvider.deleteMessage(message.id, !message.isMms)
            messageDataProvider.getServerHashForMessage(message.id)?.let { serverHash ->
                var publicKey = recipient.address.serialize()
                if (recipient.isClosedGroupRecipient) { publicKey = GroupUtil.doubleDecodeGroupID(publicKey).toHexString() }
                SnodeAPI.deleteMessage(publicKey, listOf(serverHash))
                    .failUi { error ->
                        Toast.makeText(getApplication(), "Couldn't delete message due to error: $error", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    fun deleteLocally(message: MessageRecord) {
        buildUnsendRequest(message)?.let { unsendRequest ->
            TextSecurePreferences.getLocalNumber(getApplication())?.let {
                MessageSender.send(unsendRequest, Address.fromSerialized(it))
            }
        }
        MessagingModuleConfiguration.shared.messageDataProvider.deleteMessage(message.id, !message.isMms)
    }

    private fun buildUnsendRequest(message: MessageRecord): UnsendRequest? {
        if (recipient.isOpenGroupRecipient) return null
        val messageDataProvider = MessagingModuleConfiguration.shared.messageDataProvider
        messageDataProvider.getServerHashForMessage(message.id) ?: return null
        val unsendRequest = UnsendRequest()
        if (message.isOutgoing) {
            unsendRequest.author = TextSecurePreferences.getLocalNumber(getApplication())
        } else {
            unsendRequest.author = message.individualRecipient.address.contactIdentifier()
        }
        unsendRequest.timestamp = message.timestamp

        return unsendRequest
    }

    fun deleteMessagesWithoutUnsendRequest(messages: Set<MessageRecord>) {
        val messageDataProvider = MessagingModuleConfiguration.shared.messageDataProvider
        val openGroup = lokiThreadDb.getOpenGroupChat(threadId)
        if (openGroup != null) {
            val messageServerIDs = mutableMapOf<Long, MessageRecord>()
            for (message in messages) {
                val messageServerID = lokiMessageDb.getServerID(message.id, !message.isMms) ?: continue
                messageServerIDs[messageServerID] = message
            }
            for ((messageServerID, message) in messageServerIDs) {
                OpenGroupAPIV2.deleteMessage(messageServerID, openGroup.room, openGroup.server)
                    .success {
                        messageDataProvider.deleteMessage(message.id, !message.isMms)
                    }.failUi { error ->
                        Toast.makeText(getApplication(), "Couldn't delete message due to error: $error", Toast.LENGTH_LONG).show()
                    }
            }
        } else {
            for (message in messages) {
                if (message.isMms) {
                    mmsDb.deleteMessage(message.id)
                } else {
                    smsDb.deleteMessage(message.id)
                }
            }
        }
    }

    fun banUser(messages: Set<MessageRecord>) {
        val sessionID = messages.first().individualRecipient.address.toString()
        val openGroup = lokiThreadDb.getOpenGroupChat(threadId)!!
        OpenGroupAPIV2.ban(sessionID, openGroup.room, openGroup.server).successUi {
            Toast.makeText(getApplication(), "Successfully banned user", Toast.LENGTH_LONG).show()
        }.failUi { error ->
            Toast.makeText(getApplication(), "Couldn't ban user due to error: $error", Toast.LENGTH_LONG).show()
        }
    }

    fun banAndDeleteAll(messages: Set<MessageRecord>) {
        val sessionID = messages.first().individualRecipient.address.toString()
        val openGroup = lokiThreadDb.getOpenGroupChat(threadId)!!
        OpenGroupAPIV2.banAndDeleteAll(sessionID, openGroup.room, openGroup.server).successUi {
            Toast.makeText(getApplication(), "Successfully banned user and deleted all their messages", Toast.LENGTH_LONG).show()
        }.failUi { error ->
            Toast.makeText(getApplication(), "Couldn't execute request due to error: $error", Toast.LENGTH_LONG).show()
        }
    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(threadId: Long): Factory
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @AssistedInject constructor(
        @Assisted private val threadId: Long,
        private val threadDb: ThreadDatabase,
        private val draftDb: DraftDatabase,
        private val lokiThreadDb: LokiThreadDatabase,
        private val smsDb: SmsDatabase,
        private val mmsDb: MmsDatabase,
        private val recipientDb: RecipientDatabase,
        private val lokiMessageDb: LokiMessageDatabase,
        private val application: Application
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConversationViewModel(
                threadId,
                threadDb,
                draftDb,
                lokiThreadDb,
                smsDb,
                mmsDb,
                recipientDb,
                lokiMessageDb,
                application
            ) as T
        }
    }
}
