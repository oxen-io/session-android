package org.session.libsession.messaging.jobs

import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.messages.control.UnsendRequest
import org.session.libsession.messaging.messages.visible.ParsedMessage
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.sending_receiving.*
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.messaging.utilities.SessionId
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsignal.protos.UtilProtos
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Log

data class MessageReceiveParameters(
    val data: ByteArray,
    val serverHash: String? = null,
    val openGroupMessageServerID: Long? = null,
    val reactions: Map<String, OpenGroupApi.Reaction>? = null
)

class BatchMessageReceiveJob(
    val messages: List<MessageReceiveParameters>,
    val openGroupID: String? = null
) : Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 1 // handled in JobQueue onJobFailed
    // Failure Exceptions must be retryable if they're a  MessageReceiver.Error
    val failures = mutableListOf<MessageReceiveParameters>()

    companion object {
        const val TAG = "BatchMessageReceiveJob"
        const val KEY = "BatchMessageReceiveJob"

        const val BATCH_DEFAULT_NUMBER = 512

        // Keys used for database storage
        private val NUM_MESSAGES_KEY = "numMessages"
        private val DATA_KEY = "data"
        private val SERVER_HASH_KEY = "serverHash"
        private val OPEN_GROUP_MESSAGE_SERVER_ID_KEY = "openGroupMessageServerID"
        private val OPEN_GROUP_ID_KEY = "open_group_id"
    }

    private fun getThreadId(message: Message, storage: StorageProtocol): Long {
        val senderOrSync = when (message) {
            is VisibleMessage -> message.syncTarget ?: message.sender!!
            is ExpirationTimerUpdate -> message.syncTarget ?: message.sender!!
            else -> message.sender!!
        }
        return storage.getOrCreateThreadIdFor(senderOrSync, message.groupPublicKey, openGroupID)
    }

    override fun execute(dispatcherName: String) {
        executeAsync(dispatcherName).get()
    }

    fun executeAsync(dispatcherName: String): Promise<Unit, Exception> {
        return task {
            val threadMap = mutableMapOf<Long, MutableList<ParsedMessage>>()
            val storage = MessagingModuleConfiguration.shared.storage
            val context = MessagingModuleConfiguration.shared.context
            val localUserPublicKey = storage.getUserPublicKey()
            val serverPublicKey = openGroupID?.let { storage.getOpenGroupPublicKey(it.split(".").dropLast(1).joinToString(".")) }

            // parse and collect IDs
            messages.forEach { messageParameters ->
                val (data, serverHash, openGroupMessageServerID) = messageParameters
                try {
                    val (message, proto) = MessageReceiver.parse(data, openGroupMessageServerID, openGroupPublicKey = serverPublicKey)
                    message.serverHash = serverHash
                    val threadID = getThreadId(message, storage)
                    val parsedParams = ParsedMessage(messageParameters, message, proto)
                    if (!threadMap.containsKey(threadID)) {
                        threadMap[threadID] = mutableListOf(parsedParams)
                    } else {
                        threadMap[threadID]!! += parsedParams
                    }
                } catch (e: Exception) {
                    when (e) {
                        is MessageReceiver.Error.DuplicateMessage, MessageReceiver.Error.SelfSend -> {
                            Log.i(TAG, "Couldn't receive message, failed with error: ${e.message} (id: $id)")
                        }
                        is MessageReceiver.Error -> {
                            if (!e.isRetryable) {
                                Log.e(TAG, "Couldn't receive message, failed permanently (id: $id)", e)
                            }
                            else {
                                Log.e(TAG, "Couldn't receive message, failed (id: $id)", e)
                                failures += messageParameters
                            }
                        }
                        else -> {
                            Log.e(TAG, "Couldn't receive message, failed (id: $id)", e)
                            failures += messageParameters
                        }
                    }
                }
            }

            // iterate over threads and persist them (persistence is the longest constant in the batch process operation)
            runBlocking(Dispatchers.IO) {
                val deferredThreadMap = threadMap.entries.map { (threadId, messages) ->
                    async {
                        // The LinkedHashMap should preserve insertion order
                        val messageIds = linkedMapOf<Long, Pair<Boolean, Boolean>>()

                        messages.forEach { (parameters, message, proto) ->
                            try {
                                when (message) {
                                    is VisibleMessage -> {
                                        val messageId = MessageReceiver.handleVisibleMessage(message, proto, openGroupID,
                                                runIncrement = false,
                                                runThreadUpdate = false,
                                                runProfileUpdate = true
                                        )

                                        if (messageId != null && message.reaction == null) {
                                            val isUserBlindedSender = message.sender == serverPublicKey?.let { SodiumUtilities.blindedKeyPair(it, MessagingModuleConfiguration.shared.getUserED25519KeyPair()!!) }?.let { SessionId(
                                                    IdPrefix.BLINDED, it.publicKey.asBytes).hexString }
                                            messageIds[messageId] = Pair(
                                                (message.sender == localUserPublicKey || isUserBlindedSender),
                                                message.hasMention
                                            )
                                        }
                                        parameters.openGroupMessageServerID?.let {
                                            MessageReceiver.handleOpenGroupReactions(threadId, it, parameters.reactions)
                                        }
                                    }

                                    is UnsendRequest -> {
                                        val deletedMessageId = MessageReceiver.handleUnsendRequest(message)

                                        // If we removed a message then ensure it isn't in the 'messageIds'
                                        if (deletedMessageId != null) {
                                            messageIds.remove(deletedMessageId)
                                        }
                                    }

                                    else -> MessageReceiver.handle(message, proto, openGroupID)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Couldn't process message (id: $id)", e)
                                if (e is MessageReceiver.Error && !e.isRetryable) {
                                    Log.e(TAG, "Message failed permanently (id: $id)", e)
                                } else {
                                    Log.e(TAG, "Message failed (id: $id)", e)
                                    failures += parameters
                                }
                            }
                        }
                        // increment unreads, notify, and update thread
                        val unreadFromMine = messageIds.map { it.value.first }.indexOfLast { it }
                        var trueUnreadCount = messageIds.filter { !it.value.first }.size
                        var trueUnreadMentionCount = messageIds.filter { !it.value.first && it.value.second }.size
                        if (unreadFromMine >= 0) {
                            storage.markConversationAsRead(threadId, false)

                            val trueUnreadIds = messageIds.keys.toList().subList(unreadFromMine + 1, messageIds.keys.count())
                            trueUnreadCount = trueUnreadIds.size
                            trueUnreadMentionCount = messageIds
                                    .filter { trueUnreadIds.contains(it.key) && !it.value.first && it.value.second }
                                    .size
                        }
                        if (trueUnreadCount > 0) {
                            storage.incrementUnread(threadId, trueUnreadCount, trueUnreadMentionCount)
                        }
                        storage.updateThread(threadId, true)
                        SSKEnvironment.shared.notificationManager.updateNotification(context, threadId)
                    }
                }
                // await all thread processing
                deferredThreadMap.awaitAll()
            }
            if (failures.isEmpty()) {
                handleSuccess(dispatcherName)
            } else {
                handleFailure(dispatcherName)
            }
        }
    }

    private fun handleSuccess(dispatcherName: String) {
        Log.i(TAG, "Completed processing of ${messages.size} messages (id: $id)")
        this.delegate?.handleJobSucceeded(this, dispatcherName)
    }

    private fun handleFailure(dispatcherName: String) {
        Log.i(TAG, "Handling failure of ${failures.size} messages (${messages.size - failures.size} processed successfully) (id: $id)")
        this.delegate?.handleJobFailed(this, dispatcherName, Exception("One or more jobs resulted in failure"))
    }

    override fun serialize(): Data {
        val arraySize = messages.size
        val dataArrays = UtilProtos.ByteArrayList.newBuilder()
            .addAllContent(messages.map(MessageReceiveParameters::data).map(ByteString::copyFrom))
            .build()
        val serverHashes = messages.map { it.serverHash.orEmpty() }
        val openGroupServerIds = messages.map { it.openGroupMessageServerID ?: -1L }
        return Data.Builder()
            .putInt(NUM_MESSAGES_KEY, arraySize)
            .putByteArray(DATA_KEY, dataArrays.toByteArray())
            .putString(OPEN_GROUP_ID_KEY, openGroupID)
            .putLongArray(OPEN_GROUP_MESSAGE_SERVER_ID_KEY, openGroupServerIds.toLongArray())
            .putStringArray(SERVER_HASH_KEY, serverHashes.toTypedArray())
            .build()
    }

    override fun getFactoryKey(): String = KEY

    class Factory : Job.Factory<BatchMessageReceiveJob> {
        override fun create(data: Data): BatchMessageReceiveJob {
            val numMessages = data.getInt(NUM_MESSAGES_KEY)
            val dataArrays = data.getByteArray(DATA_KEY)
            val contents =
                UtilProtos.ByteArrayList.parseFrom(dataArrays).contentList.map(ByteString::toByteArray)
            val serverHashes =
                if (data.hasStringArray(SERVER_HASH_KEY)) data.getStringArray(SERVER_HASH_KEY) else arrayOf()
            val openGroupMessageServerIDs = data.getLongArray(OPEN_GROUP_MESSAGE_SERVER_ID_KEY)
            val openGroupID = data.getStringOrDefault(OPEN_GROUP_ID_KEY, null)

            val parameters = (0 until numMessages).map { index ->
                val data = contents[index]
                val serverHash = serverHashes[index].let { if (it.isEmpty()) null else it }
                val serverId = openGroupMessageServerIDs[index].let { if (it == -1L) null else it }
                MessageReceiveParameters(data, serverHash, serverId)
            }

            return BatchMessageReceiveJob(parameters, openGroupID)
        }
    }

}