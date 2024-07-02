package org.session.libsession.messaging.sending_receiving.pollers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import network.loki.messenger.libsession_util.GroupInfoConfig
import network.loki.messenger.libsession_util.GroupKeysConfig
import network.loki.messenger.libsession_util.GroupMembersConfig
import network.loki.messenger.libsession_util.util.GroupInfo
import network.loki.messenger.libsession_util.util.GroupInfo.ClosedGroupInfo.Companion.isAuthData
import network.loki.messenger.libsession_util.util.Sodium
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.BatchMessageReceiveJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveParameters
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.snode.RawResponse
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.session.libsignal.utilities.SessionId
import org.session.libsignal.utilities.Snode
import kotlin.time.Duration.Companion.days

class ClosedGroupPoller(private val scope: CoroutineScope,
                        private val executor: CoroutineDispatcher,
                        private val closedGroupSessionId: SessionId,
                        private val configFactoryProtocol: ConfigFactoryProtocol) {

    data class ParsedRawMessage(
            val data: ByteArray,
            val hash: String,
            val timestamp: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ParsedRawMessage

            if (!data.contentEquals(other.data)) return false
            if (hash != other.hash) return false
            if (timestamp != other.timestamp) return false

            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + hash.hashCode()
            result = 31 * result + timestamp.hashCode()
            return result
        }
    }

    companion object {
        const val POLL_INTERVAL = 3_000L
        const val ENABLE_LOGGING = false
    }

    private var isRunning: Boolean = false
    private var job: Job? = null

    fun start() {
        if (isRunning) return // already started, don't restart

        if (ENABLE_LOGGING) Log.d("ClosedGroupPoller", "Starting closed group poller for ${closedGroupSessionId.hexString().take(4)}")
        job?.cancel()
        job = scope.launch(executor) {
            val closedGroups = configFactoryProtocol.userGroups ?: return@launch
            isRunning = true
            while (isActive && isRunning) {
                val group = closedGroups.getClosedGroup(closedGroupSessionId.hexString()) ?: break
                val nextPoll = poll(group)
                if (nextPoll != null) {
                    delay(nextPoll)
                } else {
                    if (ENABLE_LOGGING) Log.d("ClosedGroupPoller", "Stopping the closed group poller")
                    return@launch
                }
            }
            isRunning = false
            // assume null poll time means don't continue polling, either the group has been deleted or something else
        }
    }

    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
    }

    fun poll(group: GroupInfo.ClosedGroupInfo): Long? {
        try {
            val snode = SnodeAPI.getSingleTargetSnode(closedGroupSessionId.hexString()).get()
            val info = configFactoryProtocol.getGroupInfoConfig(closedGroupSessionId) ?: return null
            val members = configFactoryProtocol.getGroupMemberConfig(closedGroupSessionId)
                    ?: return null
            val keys = configFactoryProtocol.getGroupKeysConfig(
                closedGroupSessionId,
                info,
                members,
                free = false
            ) ?: return null

            val isAdmin = group.hasAdminKey()

            val hashesToExtend = mutableSetOf<String>()

            hashesToExtend += info.currentHashes()
            hashesToExtend += members.currentHashes()
            hashesToExtend += keys.currentHashes()

            val keysIndex = 0
            val revokedIndex = 1
            val infoIndex = 2
            val membersIndex = 3
            val messageIndex = 4

            val authData = group.signingKey()
            val signCallback = if (isAuthData(authData)) {
                SnodeAPI.subkeyCallback(authData, keys, false)
            } else SnodeAPI.signingKeyCallback(authData)

            val revokedPoll = SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                snode,
                closedGroupSessionId.hexString(),
                Namespace.REVOKED_GROUP_MESSAGES(),
                maxSize = null,
                signCallback
            ) ?: return null
            val messagePoll = SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                    snode,
                    closedGroupSessionId.hexString(),
                    Namespace.CLOSED_GROUP_MESSAGES(),
                    maxSize = null,
                    signCallback
            ) ?: return null
            val infoPoll = SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                    snode,
                    closedGroupSessionId.hexString(),
                    info.namespace(),
                    maxSize = null,
                    signCallback
            ) ?: return null
            val membersPoll = SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                    snode,
                    closedGroupSessionId.hexString(),
                    members.namespace(),
                    maxSize = null,
                    signCallback
            ) ?: return null
            val keysPoll = SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                    snode,
                    closedGroupSessionId.hexString(),
                    keys.namespace(),
                    maxSize = null,
                    signCallback
            ) ?: return null

            val requests = mutableListOf(keysPoll, revokedPoll, infoPoll, membersPoll, messagePoll)

            if (hashesToExtend.isNotEmpty()) {
                SnodeAPI.buildAuthenticatedAlterTtlBatchRequest(
                        messageHashes = hashesToExtend.toList(),
                        publicKey = closedGroupSessionId.hexString(),
                        signingKey = group.signingKey(),
                        newExpiry = SnodeAPI.nowWithOffset + 14.days.inWholeMilliseconds,
                        extend = true
                )?.let { extensionRequest ->
                    requests += extensionRequest
                }
            }

            val pollResult = SnodeAPI.getRawBatchResponse(
                    snode,
                    closedGroupSessionId.hexString(),
                    requests
            ).get()

            // If we no longer have a group, stop poller
            if (configFactoryProtocol.userGroups?.getClosedGroup(closedGroupSessionId.hexString()) == null) return null

            // if poll result body is null here we don't have any things ig
            if (ENABLE_LOGGING) Log.d("ClosedGroupPoller", "Poll results @${SnodeAPI.nowWithOffset}:")
            (pollResult["results"] as List<RawResponse>).forEachIndexed { index, response ->
                when (index) {
                    revokedIndex -> handleRevoked(response, keys)
                    keysIndex -> handleKeyPoll(response, keys, info, members)
                    infoIndex -> handleInfo(response, info)
                    membersIndex -> handleMembers(response, members)
                    messageIndex -> handleMessages(response, snode, keys)
                }
            }

            val requiresSync = info.needsPush() || members.needsPush() || keys.needsRekey() || keys.pendingConfig() != null

            if (info.needsDump() || members.needsDump() || keys.needsDump()) {
                configFactoryProtocol.saveGroupConfigs(keys, info, members)
            }
            keys.free()
            info.free()
            members.free()

            if (requiresSync) {
                configFactoryProtocol.scheduleUpdate(Destination.ClosedGroup(closedGroupSessionId.hexString()))
            }
        } catch (e: Exception) {
            if (ENABLE_LOGGING) Log.e("GroupPoller", "Polling failed for group", e)
            return POLL_INTERVAL
        }
        return POLL_INTERVAL // this might change in future
    }

    private fun parseMessages(response: RawResponse): List<ParsedRawMessage> {
        val body = response["body"] as? RawResponse
        if (body == null) {
            if (ENABLE_LOGGING) Log.e("GroupPoller", "Batch parse messages contained no body!")
            return emptyList()
        }
        val messages = body["messages"] as? List<*> ?: return emptyList()
        return messages.mapNotNull { messageMap ->
            val rawMessageAsJSON = messageMap as? Map<*, *> ?: return@mapNotNull null
            val base64EncodedData = rawMessageAsJSON["data"] as? String ?: return@mapNotNull null
            val hash = rawMessageAsJSON["hash"] as? String ?: return@mapNotNull null
            val timestamp = rawMessageAsJSON["timestamp"] as? Long ?: return@mapNotNull null
            val data = base64EncodedData.let { Base64.decode(it) }
            ParsedRawMessage(data, hash, timestamp)
        }
    }

    private fun handleRevoked(response: RawResponse, keys: GroupKeysConfig) {
        // This shouldn't ever return null at this point
        val userSessionId = configFactoryProtocol.userSessionId()!!
        val body = response["body"] as? RawResponse
        if (body == null) {
            if (ENABLE_LOGGING) Log.e("GroupPoller", "No revoked messages")
            return
        }
        val messages = body["messages"] as? List<*>
            ?: return Log.w("GroupPoller", "body didn't contain a list of messages")
        messages.forEach { messageMap ->
            val rawMessageAsJSON = messageMap as? Map<*,*>
                ?: return@forEach Log.w("GroupPoller", "rawMessage wasn't a map as expected")
            val data = rawMessageAsJSON["data"] as? String ?: return@forEach
            val hash = rawMessageAsJSON["hash"] as? String ?: return@forEach
            val timestamp = rawMessageAsJSON["timestamp"] as? Long ?: return@forEach
            Log.d("GroupPoller", "Handling message with hash $hash")

            val decoded = configFactoryProtocol.maybeDecryptForUser(
                Base64.decode(data),
                Sodium.KICKED_DOMAIN,
                closedGroupSessionId,
            )

            if (decoded != null) {
                Log.d("GroupPoller", "decoded kick message was for us")
                val message = decoded.decodeToString()
                if (Sodium.KICKED_REGEX.matches(message)) {
                    val (sessionId, generation) = message.split("-")
                    if (sessionId == userSessionId.hexString() && generation.toInt() >= keys.currentGeneration()) {
                        Log.d("GroupPoller", "We were kicked from the group, delete and stop polling")
                        stop()
                        // TODO: add in async group leaving job
                    }
                }
            }

        }
    }

    private fun handleKeyPoll(response: RawResponse,
                              keysConfig: GroupKeysConfig,
                              infoConfig: GroupInfoConfig,
                              membersConfig: GroupMembersConfig) {
        // get all the data to hash objects and process them
        val allMessages = parseMessages(response)
        if (ENABLE_LOGGING) Log.d("ClosedGroupPoller", "Total key messages this poll: ${allMessages.size}")
        var total = 0
        allMessages.forEach { (message, hash, timestamp) ->
            if (keysConfig.loadKey(message, hash, timestamp, infoConfig, membersConfig)) {
                total++
            }
            if (ENABLE_LOGGING) Log.d("ClosedGroupPoller", "Merged $hash for keys on ${closedGroupSessionId.hexString()}")
        }
        if (ENABLE_LOGGING) Log.d("ClosedGroupPoller", "Total key messages consumed: $total")
    }

    private fun handleInfo(response: RawResponse,
                           infoConfig: GroupInfoConfig) {
        val messages = parseMessages(response)
        messages.forEach { (message, hash, _) ->
            infoConfig.merge(hash to message)
            if (ENABLE_LOGGING) Log.d("ClosedGroupPoller", "Merged $hash for info on ${closedGroupSessionId.hexString()}")
        }
        if (messages.isNotEmpty()) {
            val lastTimestamp = messages.maxOf { it.timestamp }
            MessagingModuleConfiguration.shared.storage.notifyConfigUpdates(infoConfig, lastTimestamp)
        }
    }

    private fun handleMembers(response: RawResponse,
                              membersConfig: GroupMembersConfig) {
        parseMessages(response).forEach { (message, hash, _) ->
            membersConfig.merge(hash to message)
            if (ENABLE_LOGGING) Log.d("ClosedGroupPoller", "Merged $hash for members on ${closedGroupSessionId.hexString()}")
        }
    }

    private fun handleMessages(response: RawResponse, snode: Snode, keysConfig: GroupKeysConfig) {
        val body = response["body"] as RawResponse
        val messages = SnodeAPI.parseRawMessagesResponse(
            body,
            snode,
            closedGroupSessionId.hexString()
        ) {
            return@parseRawMessagesResponse keysConfig.decrypt(it)
        }
        val parameters = messages.map { (envelope, serverHash) ->
            MessageReceiveParameters(envelope.toByteArray(), serverHash = serverHash, closedGroup = Destination.ClosedGroup(closedGroupSessionId.hexString()))
        }
        parameters.chunked(BatchMessageReceiveJob.BATCH_DEFAULT_NUMBER).forEach { chunk ->
            val job = BatchMessageReceiveJob(chunk)
            JobQueue.shared.add(job)
        }
        if (ENABLE_LOGGING) Log.d("ClosedGroupPoller", "namespace for messages rx count: ${messages.size}")

    }

}