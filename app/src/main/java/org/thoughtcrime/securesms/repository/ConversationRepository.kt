package org.thoughtcrime.securesms.repository

import android.content.ContentResolver
import android.content.Context
import app.cash.copper.Query
import app.cash.copper.flow.observeQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.database.userAuth
import org.session.libsession.messaging.groups.GroupManagerV2
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.control.MessageRequestResponse
import org.session.libsession.messaging.messages.control.UnsendRequest
import org.session.libsession.messaging.messages.signal.OutgoingTextMessage
import org.session.libsession.messaging.messages.visible.OpenGroupInvitation
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeClock
import org.session.libsession.snode.utilities.await
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.database.DatabaseContentProviders
import org.thoughtcrime.securesms.database.DraftDatabase
import org.thoughtcrime.securesms.database.LokiMessageDatabase
import org.thoughtcrime.securesms.database.LokiThreadDatabase
import org.thoughtcrime.securesms.database.MmsDatabase
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.SessionJobDatabase
import org.thoughtcrime.securesms.database.SmsDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.dependencies.ConfigFactory
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
    fun setBlocked(threadId: Long, recipient: Recipient, blocked: Boolean)
    fun deleteLocally(recipient: Recipient, message: MessageRecord)
    fun deleteAllLocalMessagesInThreadFromSenderOfMessage(messageRecord: MessageRecord)
    fun setApproved(recipient: Recipient, isApproved: Boolean)
    fun isKicked(recipient: Recipient): Boolean

    suspend fun deleteForEveryone(threadId: Long, recipient: Recipient, message: MessageRecord): Result<Unit>
    suspend fun deleteMessageWithoutUnsendRequest(threadId: Long, messages: Set<MessageRecord>): Result<Unit>
    suspend fun banUser(threadId: Long, recipient: Recipient): Result<Unit>
    suspend fun banAndDeleteAll(threadId: Long, recipient: Recipient): Result<Unit>
    suspend fun deleteThread(threadId: Long): Result<Unit>
    suspend fun deleteMessageRequest(thread: ThreadRecord): Result<Unit>
    suspend fun clearAllMessageRequests(block: Boolean): Result<Unit>
    suspend fun acceptMessageRequest(threadId: Long, recipient: Recipient): Result<Unit>
    suspend fun declineMessageRequest(threadId: Long, recipient: Recipient): Result<Unit>
    fun hasReceived(threadId: Long): Boolean
    fun getInvitingAdmin(threadId: Long): Recipient?
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
    private val storage: Storage,
    private val lokiMessageDb: LokiMessageDatabase,
    private val sessionJobDb: SessionJobDatabase,
    private val configFactory: ConfigFactory,
    private val contentResolver: ContentResolver,
    private val groupManager: GroupManagerV2,
    private val clock: SnodeClock,
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
            message.sentTimestamp = clock.currentTimeMills()
            val openGroupInvitation = OpenGroupInvitation().apply {
                name = openGroup.name
                url = openGroup.joinURL
            }
            message.openGroupInvitation = openGroupInvitation
            val expirationConfig = threadDb.getOrCreateThreadIdFor(contact).let(storage::getExpirationConfiguration)
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

    override fun isKicked(recipient: Recipient): Boolean {
        // For now, we only know care we are kicked for a groups v2 recipient
        if (!recipient.isClosedGroupV2Recipient) {
            return false
        }

        return configFactory.withUserConfigs {
            it.userGroups.getClosedGroup(recipient.address.serialize())?.kicked == true
        }
    }

    // This assumes that recipient.isContactRecipient is true
    override fun setBlocked(threadId: Long, recipient: Recipient, blocked: Boolean) {
        if (recipient.isContactRecipient) {
            storage.setBlocked(listOf(recipient), blocked)
        }
    }

    override fun deleteLocally(recipient: Recipient, message: MessageRecord) {
        if (shouldSendUnsendRequest(recipient)) {
            textSecurePreferences.getLocalNumber()?.let {
                MessageSender.send(buildUnsendRequest(message), Address.fromSerialized(it))
            }
        }

        messageDataProvider.deleteMessage(message.id, !message.isMms)
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

    override suspend fun deleteForEveryone(
        threadId: Long,
        recipient: Recipient,
        message: MessageRecord
    ): Result<Unit> {
        return runCatching {
            withContext(Dispatchers.Default) {
                val openGroup = lokiThreadDb.getOpenGroupChat(threadId)
                if (openGroup != null) {
                    val serverId = lokiMessageDb.getServerID(message.id, !message.isMms)
                    if (serverId != null) {
                        OpenGroupApi.deleteMessage(
                            serverID = serverId,
                            room = openGroup.room,
                            server = openGroup.server
                        ).await()
                        messageDataProvider.deleteMessage(message.id, !message.isMms)
                    } else {
                        // If the server ID is null then this message is stuck in limbo (it has likely been
                        // deleted remotely but that deletion did not occur locally) - so we'll delete the
                        // message locally to clean up.
                        Log.w(
                            "ConversationRepository",
                            "Found community message without a server ID - deleting locally."
                        )

                        // Caution: The bool returned from `deleteMessage` is NOT "Was the message
                        // successfully deleted?" - it is "Was the thread itself also deleted because
                        // removing that message resulted in an empty thread?".
                        if (message.isMms) {
                            mmsDb.deleteMessage(message.id)
                        } else {
                            smsDb.deleteMessage(message.id)
                        }
                    }
                } else // If this thread is NOT in a Community
                {
                    val serverHash =
                        messageDataProvider.getServerHashForMessage(message.id, message.isMms)
                    if (serverHash != null) {
                        var publicKey = recipient.address.serialize()
                        if (recipient.isLegacyClosedGroupRecipient) {
                            publicKey = GroupUtil.doubleDecodeGroupID(publicKey).toHexString()
                        }

                        if (recipient.isClosedGroupV2Recipient) {
                            // admin check internally, assume either admin or all belong to user
                            groupManager.requestMessageDeletion(
                                groupId = AccountId(publicKey),
                                messageHashes = listOf(serverHash)
                            )
                        } else {
                            SnodeAPI.deleteMessage(
                                publicKey = publicKey,
                                swarmAuth = storage.userAuth!!,
                                serverHashes = listOf(serverHash)
                            ).await()
                        }

                        if (shouldSendUnsendRequest(recipient)) {
                            MessageSender.send(
                                message = buildUnsendRequest(message),
                                address = recipient.address,
                            )
                        }
                    }
                    messageDataProvider.deleteMessage(message.id, !message.isMms)
                }
            }
        }
    }

    private fun shouldSendUnsendRequest(recipient: Recipient): Boolean {
        return recipient.is1on1 || recipient.isLegacyClosedGroupRecipient
    }

    private fun buildUnsendRequest(message: MessageRecord): UnsendRequest {
        return UnsendRequest(
            author = message.takeUnless { it.isOutgoing }?.run { individualRecipient.address.contactIdentifier() } ?: textSecurePreferences.getLocalNumber(),
            timestamp = message.timestamp
        )
    }

    override suspend fun deleteMessageWithoutUnsendRequest(
        threadId: Long,
        messages: Set<MessageRecord>
    ): Result<Unit> = kotlin.runCatching {
        withContext(Dispatchers.Default) {
            val openGroup = lokiThreadDb.getOpenGroupChat(threadId)
            if (openGroup != null) {
                val messageServerIDs = mutableMapOf<Long, MessageRecord>()
                for (message in messages) {
                    val messageServerID =
                        lokiMessageDb.getServerID(message.id, !message.isMms) ?: continue
                    messageServerIDs[messageServerID] = message
                }
                messageServerIDs.forEach { (messageServerID, message) ->
                    OpenGroupApi.deleteMessage(messageServerID, openGroup.room, openGroup.server).await()
                    messageDataProvider.deleteMessage(message.id, !message.isMms)
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
    }

    override suspend fun banUser(threadId: Long, recipient: Recipient): Result<Unit> = runCatching {
        val accountID = recipient.address.toString()
        val openGroup = lokiThreadDb.getOpenGroupChat(threadId)!!
        OpenGroupApi.ban(accountID, openGroup.room, openGroup.server).await()
    }

    override suspend fun banAndDeleteAll(threadId: Long, recipient: Recipient) = runCatching {
        // Note: This accountId could be the blinded Id
        val accountID = recipient.address.toString()
        val openGroup = lokiThreadDb.getOpenGroupChat(threadId)!!

        OpenGroupApi.banAndDeleteAll(accountID, openGroup.room, openGroup.server).await()
    }

    override suspend fun deleteThread(threadId: Long) = runCatching {
        withContext(Dispatchers.Default) {
            sessionJobDb.cancelPendingMessageSendJobs(threadId)
            storage.deleteConversation(threadId)
        }
    }

    override suspend fun deleteMessageRequest(thread: ThreadRecord)
        = declineMessageRequest(thread.threadId, thread.recipient)

    override suspend fun clearAllMessageRequests(block: Boolean) = runCatching {
        withContext(Dispatchers.Default) {
            threadDb.readerFor(threadDb.unapprovedConversationList).use { reader ->
                while (reader.next != null) {
                    deleteMessageRequest(reader.current)
                    val recipient = reader.current.recipient
                    if (block && !recipient.isClosedGroupV2Recipient) {
                        setBlocked(reader.current.threadId, recipient, true)
                    }
                }
            }
        }
    }

    override suspend fun acceptMessageRequest(threadId: Long, recipient: Recipient) = runCatching {
        withContext(Dispatchers.Default) {
            storage.setRecipientApproved(recipient, true)
            if (recipient.isClosedGroupV2Recipient) {
                groupManager.respondToInvitation(
                    AccountId(recipient.address.serialize()),
                    approved = true
                )
            } else {
                val message = MessageRequestResponse(true)
                MessageSender.send(
                    message = message,
                    destination = Destination.from(recipient.address),
                    isSyncMessage = recipient.isLocalNumber
                ).await()
            }

            threadDb.setHasSent(threadId, true)
            // add a control message for our user
            storage.insertMessageRequestResponseFromYou(threadId)
        }
    }

    override suspend fun declineMessageRequest(threadId: Long, recipient: Recipient): Result<Unit> = runCatching {
        withContext(Dispatchers.Default) {
            sessionJobDb.cancelPendingMessageSendJobs(threadId)
            if (recipient.isClosedGroupV2Recipient) {
                groupManager.respondToInvitation(
                    AccountId(recipient.address.serialize()),
                    approved = false
                )
            } else {
                storage.deleteConversation(threadId)
            }
        }
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

    // Only call this with a closed group thread ID
    override fun getInvitingAdmin(threadId: Long): Recipient? {
        return lokiMessageDb.groupInviteReferrer(threadId)?.let { id ->
            Recipient.from(context, Address.fromSerialized(id), false)
        }
    }
}