package org.session.libsession.messaging.sending_receiving.pollers

import com.google.protobuf.ByteString
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.BatchMessageReceiveJob
import org.session.libsession.messaging.jobs.GroupAvatarDownloadJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveJob
import org.session.libsession.messaging.jobs.MessageReceiveParameters
import org.session.libsession.messaging.jobs.OpenGroupDeleteJob
import org.session.libsession.messaging.jobs.TrimThreadJob
import org.session.libsession.messaging.open_groups.Endpoint
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.open_groups.OpenGroupMessage
import org.session.libsession.messaging.sending_receiving.MessageReceiver
import org.session.libsession.messaging.sending_receiving.handle
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Base64
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
            responses.filterNot { it.body == null }.forEach { response ->
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
        storage.setServerCapabilities(server, capabilities.capabilities)
    }

    private fun handleRoomPollInfo(
        server: String,
        roomToken: String,
        pollInfo: OpenGroupApi.RoomPollInfo
    ) {
        val storage = MessagingModuleConfiguration.shared.storage
        val groupId = "$server.$roomToken"

        val existingOpenGroup = storage.getOpenGroup(roomToken, server)
        val publicKey = existingOpenGroup?.publicKey ?: return
        val openGroup = OpenGroup(
            server = server,
            room = pollInfo.token,
            name = pollInfo.details?.name ?: "",
            infoUpdates = pollInfo.details?.infoUpdates ?: 0,
            publicKey = publicKey,
        )
        // - Open Group changes
        storage.updateOpenGroup(openGroup)

        // - User Count
        storage.setUserCount(roomToken, server, pollInfo.activeUsers)

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
            OpenGroupMessage(
                serverID = it.id,
                sender = it.sessionId,
                sentTimestamp = TimeUnit.SECONDS.toMillis(it.posted),
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
        sortedMessages.forEach {
            val encodedMessage = Base64.decode(it.base64EncodedMessage)
            val envelope = SignalServiceProtos.Envelope.newBuilder()
                .setTimestamp(TimeUnit.SECONDS.toMillis(it.postedAt))
                .setType(SignalServiceProtos.Envelope.Type.SESSION_MESSAGE)
                .setContent(ByteString.copyFrom(encodedMessage))
                .setSource(it.sender)
                .build()
            val (message, proto) = MessageReceiver.parse(envelope.toByteArray(), it.id, fromOutbox, serverPublicKey)
            MessageReceiver.handle(message, proto, null)
        }
    }

    private fun handleNewMessages(
        room: String,
        openGroupID: String,
        messages: List<OpenGroupMessage>
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

        if (envelopes.isNotEmpty()) {
            JobQueue.shared.add(TrimThreadJob(threadId,openGroupID))
        }

        val indicatedMax = messages.mapNotNull { it.serverID }.maxOrNull() ?: 0
        val currentLastMessageServerID = storage.getLastMessageServerID(room, server) ?: 0
        val actualMax = max(indicatedMax, currentLastMessageServerID)
        if (actualMax > 0 && indicatedMax > currentLastMessageServerID) {
            storage.setLastMessageServerID(room, server, actualMax)
        }
    }

    private fun handleDeletedMessages(room: String, openGroupID: String, deletions: List<OpenGroupApi.MessageDeletion>) {
        val storage = MessagingModuleConfiguration.shared.storage
        val groupID = GroupUtil.getEncodedOpenGroupID(openGroupID.toByteArray())
        val threadID = storage.getThreadId(Address.fromSerialized(groupID)) ?: return

        val serverIds = deletions.map { deletion ->
            deletion.deletedMessageServerID
        }

        if (serverIds.isNotEmpty()) {
            val deleteJob = OpenGroupDeleteJob(serverIds.toLongArray(), threadID, openGroupID)
            JobQueue.shared.add(deleteJob)
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