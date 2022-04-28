package org.session.libsession.messaging.sending_receiving.pollers

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.BatchMessageReceiveJob
import org.session.libsession.messaging.jobs.GroupAvatarDownloadJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveJob
import org.session.libsession.messaging.jobs.MessageReceiveParameters
import org.session.libsession.messaging.open_groups.Endpoint
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.open_groups.OpenGroupMessageV2
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.successBackground
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max

class OpenGroupPoller(private val server: String, private val executorService: ScheduledExecutorService?) {
    var hasStarted = false
    var isCaughtUp = false
    var secondToLastJob: MessageReceiveJob? = null
    private var future: ScheduledFuture<*>? = null
    private val moderators = mutableMapOf<String, Set<String>>()
    private val admins = mutableMapOf<String, Set<String>>()

    companion object {
        private const val pollInterval: Long = 4000L
        const val maxInactivityPeriod = 14 * 24 * 60 * 60 * 1000
    }

    fun startIfNeeded() {
        if (hasStarted) { return }
        hasStarted = true
        future = executorService?.schedule(::poll, 0, TimeUnit.MILLISECONDS)
    }

    fun stop() {
        future?.cancel(false)
        hasStarted = false
    }

    fun poll(): Promise<Unit, Exception> {
        val storage = MessagingModuleConfiguration.shared.storage
        val rooms = storage.getAllOpenGroups().values.filter { it.server == server }.map { it.room }
        rooms.forEach { downloadGroupAvatarIfNeeded(it) }
        return OpenGroupApi.poll(rooms, server).successBackground { responses ->
            responses.forEach { response ->
                when (response.endpoint) {
                    is Endpoint.Capabilities -> {
                        handleCapabilities(server, response.body as OpenGroupApi.Capabilities)
                    }
                    is Endpoint.RoomPollInfo -> {
                        handleRoomPollInfo(server, response.endpoint.roomToken, response.body as OpenGroupApi.RoomPollInfo)
                    }
                    is Endpoint.RoomMessagesRecent -> {
                        handleMessages(server, response.endpoint.roomToken, response.body as List<OpenGroupApi.Message>)
                    }
                    is Endpoint.RoomMessagesSince  -> {
                        handleMessages(server, response.endpoint.roomToken, response.body as List<OpenGroupApi.Message>)
                    }
                    is Endpoint.Inbox, is Endpoint.InboxSince -> {
                        handleDirectMessages(server, false, response.body as List<OpenGroupApi.DirectMessage>)
                    }
                    is Endpoint.Outbox, is Endpoint.OutboxSince -> {
                        handleDirectMessages(server, true, response.body as List<OpenGroupApi.DirectMessage>)
                    }
                }
                if (secondToLastJob == null && !isCaughtUp) {
                    isCaughtUp = true
                }
            }
        }.always {
            executorService?.schedule(this@OpenGroupPoller::poll, pollInterval, TimeUnit.MILLISECONDS)
        }.map { }
    }

    private fun handleCapabilities(server: String, capabilities: OpenGroupApi.Capabilities) {
        val storage = MessagingModuleConfiguration.shared.storage
        storage.updateOpenGroupCapabilities(server, capabilities.capabilities)
    }

    private fun handleRoomPollInfo(
        server: String,
        roomToken: String,
        pollInfo: OpenGroupApi.RoomPollInfo
    ) {
        val storage = MessagingModuleConfiguration.shared.storage
        val groupId = "$server.$roomToken"
        val openGroupId = GroupUtil.getEncodedOpenGroupID(groupId.toByteArray())
        val threadId = storage.getThreadId(openGroupId)
        val userPublicKey = storage.getUserPublicKey() ?: ""

        val existingOpenGroup = storage.getOpenGroup(roomToken, server)
        val publicKey = existingOpenGroup?.publicKey ?: return
        val openGroup = OpenGroup(
            server = server,
            room = pollInfo.token,
            name = pollInfo.details?.name ?: "",
            infoUpdates = pollInfo.details?.info_updates ?: 0,
            publicKey = publicKey,
            capabilities = listOf()
        )
        // - Open Group changes
        storage.updateOpenGroup(openGroup)

        // - User Count
        storage.setUserCount(roomToken, server, pollInfo.active_users)

        // - Moderators
        pollInfo.details?.moderators?.let {
            moderators[groupId] = it.toMutableSet()
        }
        // - Admins
        pollInfo.details?.admins?.let {
            admins[groupId] = it.toMutableSet()
        }
    }

    private fun handleMessages(
        server: String,
        roomToken: String,
        messages: List<OpenGroupApi.Message>
    ) {
        val openGroupId = "$server.$roomToken"
        val (deletions, additions) = messages.sortedBy { it.seqno }.partition { it.data.isNullOrBlank() }
        handleNewMessages(roomToken, openGroupId, additions.map {
            OpenGroupMessageV2(
                serverID = it.id,
                sender = it.session_id,
                sentTimestamp = it.posted,
                base64EncodedData = it.data!!,
                base64EncodedSignature = it.signature
            )
        })
        handleDeletedMessages(roomToken, openGroupId, deletions.map {
            OpenGroupApi.MessageDeletion(it.id, it.seqno)
        })
    }

    private fun handleDirectMessages(
        server: String,
        fromOutbox: Boolean,
        messages: List<OpenGroupApi.DirectMessage>
    ) {
        if (messages.isEmpty()) return
        val storage = MessagingModuleConfiguration.shared.storage
        val serverPublicKey = storage.getOpenGroupPublicKey(server)
        val sortedMessages = messages.sortedBy { it.id }
        val lastMessageId = sortedMessages.last().id
        if (fromOutbox) {
            storage.setLastOutboxMessageId(server, lastMessageId)
        } else {
            storage.setLastInboxMessageId(server, lastMessageId)
        }

    }

    private fun handleNewMessages(
        room: String,
        openGroupID: String,
        messages: List<OpenGroupMessageV2>
    ) {
        val storage = MessagingModuleConfiguration.shared.storage
        val groupID = GroupUtil.getEncodedOpenGroupID(openGroupID.toByteArray())
        // check thread still exists
        val threadId = storage.getThreadId(Address.fromSerialized(groupID)) ?: -1
        val threadExists = threadId >= 0
        if (!hasStarted || !threadExists) { return }
        val envelopes = messages.sortedBy { it.serverID!! }.map { message ->
            val senderPublicKey = message.sender!!
            val builder = SignalServiceProtos.Envelope.newBuilder()
            builder.type = SignalServiceProtos.Envelope.Type.SESSION_MESSAGE
            builder.source = senderPublicKey
            builder.sourceDevice = 1
            builder.content = message.toProto().toByteString()
            builder.timestamp = message.sentTimestamp
            builder.build() to message.serverID
        }

        envelopes.chunked(BatchMessageReceiveJob.BATCH_DEFAULT_NUMBER).forEach { list ->
            val parameters = list.map { (message, serverId) ->
                MessageReceiveParameters(message.toByteArray(), openGroupMessageServerID = serverId)
            }
            JobQueue.shared.add(BatchMessageReceiveJob(parameters, openGroupID))
        }

        val currentLastMessageServerID = storage.getLastMessageServerID(room, server) ?: 0
        val actualMax = max(messages.mapNotNull { it.serverID }.maxOrNull() ?: 0, currentLastMessageServerID)
        if (actualMax > 0) {
            storage.setLastMessageServerID(room, server, actualMax)
        }
    }

    private fun handleDeletedMessages(room: String, openGroupID: String, deletions: List<OpenGroupApi.MessageDeletion>) {
        val storage = MessagingModuleConfiguration.shared.storage
        val dataProvider = MessagingModuleConfiguration.shared.messageDataProvider
        val groupID = GroupUtil.getEncodedOpenGroupID(openGroupID.toByteArray())
        val threadID = storage.getThreadId(Address.fromSerialized(groupID)) ?: return
        val deletedMessageIDs = deletions.mapNotNull { deletion ->
            dataProvider.getMessageID(deletion.deletedMessageServerID, threadID)
        }
        deletedMessageIDs.forEach { (messageId, isSms) ->
            MessagingModuleConfiguration.shared.messageDataProvider.deleteMessage(messageId, isSms)
        }
        val currentMax = storage.getLastDeletionServerID(room, server) ?: 0L
        val latestMax = deletions.map { it.id }.maxOrNull() ?: 0L
        if (latestMax > currentMax && latestMax != 0L) {
            storage.setLastDeletionServerID(room, server, latestMax)
        }
    }

    private fun downloadGroupAvatarIfNeeded(room: String) {
        val storage = MessagingModuleConfiguration.shared.storage
        if (storage.getGroupAvatarDownloadJob(server, room) != null) return
        val groupId = GroupUtil.getEncodedOpenGroupID("$server.$room".toByteArray())
        storage.getGroup(groupId)?.let {
            if (System.currentTimeMillis() > it.updatedTimestamp + TimeUnit.DAYS.toMillis(7)) {
                JobQueue.shared.add(GroupAvatarDownloadJob(room, server))
            }
        }
    }

}