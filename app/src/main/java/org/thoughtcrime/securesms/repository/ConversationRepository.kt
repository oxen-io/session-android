package org.thoughtcrime.securesms.repository

import network.loki.messenger.libsession_util.util.ExpiryMode

import android.content.ContentResolver
import android.content.Context
import app.cash.copper.Query
import app.cash.copper.flow.observeQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.MarkAsDeletedMessage
import org.session.libsession.messaging.messages.control.MessageRequestResponse
import org.session.libsession.messaging.messages.control.UnsendRequest
import org.session.libsession.messaging.messages.signal.OutgoingTextMessage
import org.session.libsession.messaging.messages.visible.OpenGroupInvitation
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.database.DatabaseContentProviders
import org.thoughtcrime.securesms.database.DraftDatabase
import org.thoughtcrime.securesms.database.ExpirationConfigurationDatabase
import org.thoughtcrime.securesms.database.LokiMessageDatabase
import org.thoughtcrime.securesms.database.LokiThreadDatabase
import org.thoughtcrime.securesms.database.MmsDatabase
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.SessionJobDatabase
import org.thoughtcrime.securesms.database.SmsDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import javax.inject.Inject

interface ConversationRepository {
    fun maybeGetRecipientForThreadId(threadId: Long): Recipient?
    fun maybeGetBlindedRecipient(recipient: Recipient): Recipient?
    fun changes(threadId: Long): Flow<Query>
    fun recipientUpdateFlow(threadId: Long): Flow<Recipient?>
    fun saveDraft(threadId: Long, text: String)
    fun getDraft(threadId: Long): String?
    fun clearDrafts(threadId: Long)
    fun inviteContacts(threadId: Long, contacts: List<Recipient>)
    fun setBlocked(recipient: Recipient, blocked: Boolean)
    fun markAsDeletedLocally(messages: Set<MessageRecord>, displayedMessage: String)
    fun deleteMessages(messages: Set<MessageRecord>, threadId: Long)
    fun deleteAllLocalMessagesInThreadFromSenderOfMessage(messageRecord: MessageRecord)
    fun setApproved(recipient: Recipient, isApproved: Boolean)
    suspend fun markAsDeletedForEveryone(threadId: Long, recipient: Recipient, message: MessageRecord): ResultOf<Unit>
    fun buildUnsendRequest(recipient: Recipient, message: MessageRecord): UnsendRequest?
    suspend fun banUser(threadId: Long, recipient: Recipient): ResultOf<Unit>
    suspend fun banAndDeleteAll(threadId: Long, recipient: Recipient): ResultOf<Unit>
    suspend fun deleteThread(threadId: Long): ResultOf<Unit>
    suspend fun deleteMessageRequest(thread: ThreadRecord): ResultOf<Unit>
    suspend fun clearAllMessageRequests(block: Boolean): ResultOf<Unit>
    suspend fun acceptMessageRequest(threadId: Long, recipient: Recipient): ResultOf<Unit>
    fun declineMessageRequest(threadId: Long)
    fun hasReceived(threadId: Long): Boolean
}

class DefaultConversationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val textSecurePreferences: TextSecurePreferences,
    private val messageDataProvider: MessageDataProvider,
    private val threadDb: ThreadDatabase,
    private val draftDb: DraftDatabase,
    private val lokiThreadDb: LokiThreadDatabase,
    private val smsDb: SmsDatabase,
    private val mmsDb: MmsDatabase,
    private val mmsSmsDb: MmsSmsDatabase,
    private val recipientDb: RecipientDatabase,
    private val storage: Storage,
    private val lokiMessageDb: LokiMessageDatabase,
    private val sessionJobDb: SessionJobDatabase,
    private val configDb: ExpirationConfigurationDatabase,
    private val configFactory: ConfigFactory,
    private val contentResolver: ContentResolver,
) : ConversationRepository {

    override fun maybeGetRecipientForThreadId(threadId: Long): Recipient? {
        return threadDb.getRecipientForThreadId(threadId)
    }

    override fun maybeGetBlindedRecipient(recipient: Recipient): Recipient? {
        if (!recipient.isOpenGroupInboxRecipient) return null
        return Recipient.from(
            context,
            Address.fromSerialized(GroupUtil.getDecodedOpenGroupInboxAccountId(recipient.address.serialize())),
            false
        )
    }

    override fun changes(threadId: Long): Flow<Query> =
        contentResolver.observeQuery(DatabaseContentProviders.Conversation.getUriForThread(threadId))

    override fun recipientUpdateFlow(threadId: Long): Flow<Recipient?> {
        return contentResolver.observeQuery(DatabaseContentProviders.Conversation.getUriForThread(threadId)).map {
            maybeGetRecipientForThreadId(threadId)
        }
    }

    override fun saveDraft(threadId: Long, text: String) {
        if (text.isEmpty()) return
        val drafts = DraftDatabase.Drafts()
        drafts.add(DraftDatabase.Draft(DraftDatabase.Draft.TEXT, text))
        draftDb.insertDrafts(threadId, drafts)
    }

    override fun getDraft(threadId: Long): String? {
        val drafts = draftDb.getDrafts(threadId)
        return drafts.find { it.type == DraftDatabase.Draft.TEXT }?.value
    }

    override fun clearDrafts(threadId: Long) {
        draftDb.clearDrafts(threadId)
    }

    override fun inviteContacts(threadId: Long, contacts: List<Recipient>) {
        val openGroup = lokiThreadDb.getOpenGroupChat(threadId) ?: return
        for (contact in contacts) {
            val message = VisibleMessage()
            message.sentTimestamp = SnodeAPI.nowWithOffset
            val openGroupInvitation = OpenGroupInvitation().apply {
                name = openGroup.name
                url = openGroup.joinURL
            }
            message.openGroupInvitation = openGroupInvitation
            val expirationConfig = DatabaseComponent.get(context).threadDatabase().getOrCreateThreadIdFor(contact).let(storage::getExpirationConfiguration)
            val expiresInMillis = expirationConfig?.expiryMode?.expiryMillis ?: 0
            val expireStartedAt = if (expirationConfig?.expiryMode is ExpiryMode.AfterSend) message.sentTimestamp!! else 0
            val outgoingTextMessage = OutgoingTextMessage.fromOpenGroupInvitation(
                openGroupInvitation,
                contact,
                message.sentTimestamp,
                expiresInMillis,
                expireStartedAt
            )
            smsDb.insertMessageOutbox(-1, outgoingTextMessage, message.sentTimestamp!!, true)
            MessageSender.send(message, contact.address)
        }
    }

    // This assumes that recipient.isContactRecipient is true
    override fun setBlocked(recipient: Recipient, blocked: Boolean) {
        storage.setBlocked(listOf(recipient), blocked)
    }

    /**
     * This will delete these messages from the db
     * Not to be confused with 'marking messages as deleted'
     */
    override fun deleteMessages(messages: Set<MessageRecord>, threadId: Long) {
        // split the messages into mms and sms
        val (mms, sms) = messages.partition { it.isMms }

        if(mms.isNotEmpty()){
            messageDataProvider.deleteMessages(mms.map { it.id }, threadId, isSms = false)
        }

        if(sms.isNotEmpty()){
            messageDataProvider.deleteMessages(sms.map { it.id }, threadId, isSms = true)
        }
    }

    /**
     * This will mark the messages as deleted.
     * They won't be removed from the db but instead will appear as a special type
     * of message that says something like "This message was deleted"
     */
    override fun markAsDeletedLocally(messages: Set<MessageRecord>, displayedMessage: String) {
        // split the messages into mms and sms
        val (mms, sms) = messages.partition { it.isMms }

        if(mms.isNotEmpty()){
            messageDataProvider.markMessagesAsDeleted(mms.map { MarkAsDeletedMessage(
                messageId = it.id,
                isOutgoing = it.isOutgoing
            ) },
                isSms = false,
                displayedMessage = displayedMessage
            )
        }

        if(sms.isNotEmpty()){
            messageDataProvider.markMessagesAsDeleted(sms.map { MarkAsDeletedMessage(
                messageId = it.id,
                isOutgoing = it.isOutgoing
            ) },
                isSms = true,
                displayedMessage = displayedMessage
            )
        }

        //todo DELETION delete attachments and links
        //todo DELETION delete notifications
    }

    override fun deleteAllLocalMessagesInThreadFromSenderOfMessage(messageRecord: MessageRecord) {
        val threadId = messageRecord.threadId
        val senderId = messageRecord.recipient.address.contactIdentifier()
        val messageRecordsToRemoveFromLocalStorage = mmsSmsDb.getAllMessageRecordsFromSenderInThread(threadId, senderId)
        for (message in messageRecordsToRemoveFromLocalStorage) {
            messageDataProvider.deleteMessage(message.id, !message.isMms)
        }
    }

    override fun setApproved(recipient: Recipient, isApproved: Boolean) {
        storage.setRecipientApproved(recipient, isApproved)
    }

    override suspend fun markAsDeletedForEveryone(
        threadId: Long,
        recipient: Recipient,
        message: MessageRecord
    ): ResultOf<Unit> = suspendCoroutine { continuation ->
        buildUnsendRequest(recipient, message)?.let { unsendRequest ->
            MessageSender.send(unsendRequest, recipient.address)
        }

        val openGroup = lokiThreadDb.getOpenGroupChat(threadId)
        if (openGroup != null) {
            val serverId = lokiMessageDb.getServerID(message.id, !message.isMms)?.let { messageServerID ->
                OpenGroupApi.deleteMessage(messageServerID, openGroup.room, openGroup.server)
                    .success {
                        messageDataProvider.deleteMessage(message.id, !message.isMms)
                        continuation.resume(ResultOf.Success(Unit))
                    }.fail { error ->
                        Log.w("TAG", "Call to OpenGroupApi.deleteForEveryone failed - attempting to resume..")
                        continuation.resumeWithException(error)
                    }
            }

            // If the server ID is null then this message is stuck in limbo (it has likely been
            // deleted remotely but that deletion did not occur locally) - so we'll delete the
            // message locally to clean up.
            if (serverId == null) {
                Log.w("ConversationRepository","Found community message without a server ID - deleting locally.")

                // Caution: The bool returned from `deleteMessage` is NOT "Was the message
                // successfully deleted?" - it is "Was the thread itself also deleted because
                // removing that message resulted in an empty thread?".
                if (message.isMms) {
                    mmsDb.deleteMessage(message.id)
                } else {
                    smsDb.deleteMessage(message.id)
                }
            }
        }
        else // If this thread is NOT in a Community
        {
            messageDataProvider.deleteMessage(message.id, !message.isMms)
            messageDataProvider.getServerHashForMessage(message.id, message.isMms)?.let { serverHash ->
                var publicKey = recipient.address.serialize()
                if (recipient.isClosedGroupRecipient) {
                    publicKey = GroupUtil.doubleDecodeGroupID(publicKey).toHexString()
                }
                SnodeAPI.deleteMessage(publicKey, listOf(serverHash))
                    .success {
                        continuation.resume(ResultOf.Success(Unit))
                    }.fail { error ->
                        Log.w("ConversationRepository", "Call to SnodeAPI.deleteMessage failed - attempting to resume..")
                        continuation.resumeWithException(error)
                    }
            }
        }
    }

    override fun buildUnsendRequest(recipient: Recipient, message: MessageRecord): UnsendRequest? {
        if (recipient.isCommunityRecipient) return null
        messageDataProvider.getServerHashForMessage(message.id, message.isMms) ?: return null
        return UnsendRequest(
            author = message.takeUnless { it.isOutgoing }?.run { individualRecipient.address.contactIdentifier() } ?: textSecurePreferences.getLocalNumber(),
            timestamp = message.timestamp
        )
    }

    override suspend fun banUser(threadId: Long, recipient: Recipient): ResultOf<Unit> =
        suspendCoroutine { continuation ->
            val accountID = recipient.address.toString()
            val openGroup = lokiThreadDb.getOpenGroupChat(threadId)!!
            OpenGroupApi.ban(accountID, openGroup.room, openGroup.server)
                .success {
                    continuation.resume(ResultOf.Success(Unit))
                }.fail { error ->
                    continuation.resumeWithException(error)
                }
        }

    override suspend fun banAndDeleteAll(threadId: Long, recipient: Recipient): ResultOf<Unit> =
        suspendCoroutine { continuation ->
            // Note: This accountId could be the blinded Id
            val accountID = recipient.address.toString()
            val openGroup = lokiThreadDb.getOpenGroupChat(threadId)!!

            OpenGroupApi.banAndDeleteAll(accountID, openGroup.room, openGroup.server)
                .success {
                    continuation.resume(ResultOf.Success(Unit))
                }.fail { error ->
                    continuation.resumeWithException(error)
                }
        }

    override suspend fun deleteThread(threadId: Long): ResultOf<Unit> {
        sessionJobDb.cancelPendingMessageSendJobs(threadId)
        storage.deleteConversation(threadId)
        return ResultOf.Success(Unit)
    }

    override suspend fun deleteMessageRequest(thread: ThreadRecord): ResultOf<Unit> {
        sessionJobDb.cancelPendingMessageSendJobs(thread.threadId)
        storage.deleteConversation(thread.threadId)
        return ResultOf.Success(Unit)
    }

    override suspend fun clearAllMessageRequests(block: Boolean): ResultOf<Unit> {
        threadDb.readerFor(threadDb.unapprovedConversationList).use { reader ->
            while (reader.next != null) {
                deleteMessageRequest(reader.current)
                val recipient = reader.current.recipient
                if (block) { setBlocked(recipient, true) }
            }
        }
        return ResultOf.Success(Unit)
    }

    override suspend fun acceptMessageRequest(threadId: Long, recipient: Recipient): ResultOf<Unit> = suspendCoroutine { continuation ->
        storage.setRecipientApproved(recipient, true)
        val message = MessageRequestResponse(true)
        MessageSender.send(message, Destination.from(recipient.address), isSyncMessage = recipient.isLocalNumber)
            .success {
                threadDb.setHasSent(threadId, true)
                continuation.resume(ResultOf.Success(Unit))
            }.fail { error ->
                continuation.resumeWithException(error)
            }
    }

    override fun declineMessageRequest(threadId: Long) {
        sessionJobDb.cancelPendingMessageSendJobs(threadId)
        storage.deleteConversation(threadId)
    }

    override fun hasReceived(threadId: Long): Boolean {
        val cursor = mmsSmsDb.getConversation(threadId, true)
        mmsSmsDb.readerFor(cursor).use { reader ->
            while (reader.next != null) {
                if (!reader.current.isOutgoing) { return true }
            }
        }
        return false
    }

}