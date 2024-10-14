package org.thoughtcrime.securesms.repository

import android.content.ContentResolver
import android.content.Context
import app.cash.copper.Query
import app.cash.copper.flow.observeQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import network.loki.messenger.libsession_util.util.ExpiryMode
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
import org.session.libsession.snode.utilities.await
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

    suspend fun deleteCommunityMessagesRemotely(threadId: Long, messages: Set<MessageRecord>)
    suspend fun delete1on1MessagesRemotely(
        threadId: Long,
        recipient: Recipient,
        messages: Set<MessageRecord>
    )
    suspend fun deleteNoteToSelfMessagesRemotely(
        threadId: Long,
        recipient: Recipient,
        messages: Set<MessageRecord>
    )
    suspend fun deleteLegacyGroupMessagesRemotely(
        recipient: Recipient,
        messages: Set<MessageRecord>
    )

    fun buildUnsendRequest(recipient: Recipient, message: MessageRecord): UnsendRequest?
    suspend fun banUser(threadId: Long, recipient: Recipient): Result<Unit>
    suspend fun banAndDeleteAll(threadId: Long, recipient: Recipient): Result<Unit>
    suspend fun deleteThread(threadId: Long): Result<Unit>
    suspend fun deleteMessageRequest(thread: ThreadRecord): Result<Unit>
    suspend fun clearAllMessageRequests(block: Boolean): Result<Unit>
    suspend fun acceptMessageRequest(threadId: Long, recipient: Recipient): Result<Unit>

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

            // delete reactions
            storage.deleteReactions(messageIds = mms.map { it.id }, mms = true)
        }

        if(sms.isNotEmpty()){
            messageDataProvider.markMessagesAsDeleted(sms.map { MarkAsDeletedMessage(
                messageId = it.id,
                isOutgoing = it.isOutgoing
            ) },
                isSms = true,
                displayedMessage = displayedMessage
            )

            // delete reactions
            storage.deleteReactions(messageIds = sms.map { it.id }, mms = false)
        }
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

    override suspend fun deleteCommunityMessagesRemotely(
        threadId: Long,
        messages: Set<MessageRecord>
    ) {
        val community = checkNotNull(lokiThreadDb.getOpenGroupChat(threadId)) { "Not a community" }

        messages.forEach { message ->
            lokiMessageDb.getServerID(message.id, !message.isMms)?.let { messageServerID ->
                OpenGroupApi.deleteMessage(messageServerID, community.room, community.server).await()
            }
        }
    }

    override suspend fun delete1on1MessagesRemotely(
        threadId: Long,
        recipient: Recipient,
        messages: Set<MessageRecord>
    ) {
        // delete the messages remotely
        val publicKey = recipient.address.serialize()
        val userAddress: Address? =  textSecurePreferences.getLocalNumber()?.let { Address.fromSerialized(it) }

        messages.forEach { message ->
            // delete from swarm
            messageDataProvider.getServerHashForMessage(message.id, message.isMms)
                ?.let { serverHash ->
                    SnodeAPI.deleteMessage(publicKey, listOf(serverHash))
                }

            // send an UnsendRequest to user's swarm
            buildUnsendRequest(recipient, message)?.let { unsendRequest ->
                userAddress?.let { MessageSender.send(unsendRequest, it) }
            }

            // send an UnsendRequest to recipient's swarm
            buildUnsendRequest(recipient, message)?.let { unsendRequest ->
                MessageSender.send(unsendRequest, recipient.address)
            }
        }
    }

    override suspend fun deleteLegacyGroupMessagesRemotely(
        recipient: Recipient,
        messages: Set<MessageRecord>
    ) {
        if (recipient.isClosedGroupRecipient) {
            val publicKey = recipient.address

            messages.forEach { message ->
                // send an UnsendRequest to group's swarm
                buildUnsendRequest(recipient, message)?.let { unsendRequest ->
                    MessageSender.send(unsendRequest, publicKey)
                }
            }
        }
    }

    override suspend fun deleteNoteToSelfMessagesRemotely(
        threadId: Long,
        recipient: Recipient,
        messages: Set<MessageRecord>
    ) {
        // delete the messages remotely
        val publicKey = recipient.address.serialize()
        val userAddress: Address? =  textSecurePreferences.getLocalNumber()?.let { Address.fromSerialized(it) }

        messages.forEach { message ->
            // delete from swarm
            messageDataProvider.getServerHashForMessage(message.id, message.isMms)
                ?.let { serverHash ->
                    SnodeAPI.deleteMessage(publicKey, listOf(serverHash))
                }

            // send an UnsendRequest to user's swarm
            buildUnsendRequest(recipient, message)?.let { unsendRequest ->
                userAddress?.let { MessageSender.send(unsendRequest, it) }
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

    override suspend fun banUser(threadId: Long, recipient: Recipient): Result<Unit> =
        suspendCoroutine { continuation ->
            val accountID = recipient.address.toString()
            val openGroup = lokiThreadDb.getOpenGroupChat(threadId)!!
            OpenGroupApi.ban(accountID, openGroup.room, openGroup.server)
                .success {
                    continuation.resume(Result.success(Unit))
                }.fail { error ->
                    continuation.resume(Result.failure(error))
                }
        }

    override suspend fun banAndDeleteAll(threadId: Long, recipient: Recipient): Result<Unit> =
        suspendCoroutine { continuation ->
            // Note: This accountId could be the blinded Id
            val accountID = recipient.address.toString()
            val openGroup = lokiThreadDb.getOpenGroupChat(threadId)!!

            OpenGroupApi.banAndDeleteAll(accountID, openGroup.room, openGroup.server)
                .success {
                    continuation.resume(Result.success(Unit))
                }.fail { error ->
                    continuation.resume(Result.failure(error))
                }
        }

    override suspend fun deleteThread(threadId: Long): Result<Unit> {
        sessionJobDb.cancelPendingMessageSendJobs(threadId)
        storage.deleteConversation(threadId)
        return Result.success(Unit)
    }

    override suspend fun deleteMessageRequest(thread: ThreadRecord): Result<Unit> {
        sessionJobDb.cancelPendingMessageSendJobs(thread.threadId)
        storage.deleteConversation(thread.threadId)
        return Result.success(Unit)
    }

    override suspend fun clearAllMessageRequests(block: Boolean): Result<Unit> {
        threadDb.readerFor(threadDb.unapprovedConversationList).use { reader ->
            while (reader.next != null) {
                deleteMessageRequest(reader.current)
                val recipient = reader.current.recipient
                if (block) { setBlocked(recipient, true) }
            }
        }
        return Result.success(Unit)
    }

    override suspend fun acceptMessageRequest(threadId: Long, recipient: Recipient): Result<Unit> = suspendCoroutine { continuation ->
        storage.setRecipientApproved(recipient, true)
        val message = MessageRequestResponse(true)
        MessageSender.send(message, Destination.from(recipient.address), isSyncMessage = recipient.isLocalNumber)
            .success {
                threadDb.setHasSent(threadId, true)
                // add a control message for our user
                storage.insertMessageRequestResponseFromYou(threadId)
                continuation.resume(Result.success(Unit))
            }.fail { error ->
                continuation.resume(Result.failure(error))
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