package org.session.libsession.messaging.sending_receiving.pollers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import network.loki.messenger.libsession_util.util.Sodium
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.groups.GroupManagerV2
import org.session.libsession.messaging.jobs.BatchMessageReceiveJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveParameters
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.snode.RawResponse
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeClock
import org.session.libsession.snode.model.RetrieveMessageResponse
import org.session.libsession.snode.utilities.await
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.ConfigMessage
import org.session.libsession.utilities.getClosedGroup
import org.session.libsignal.database.LokiAPIDatabaseProtocol
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.session.libsignal.utilities.Snode
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.days

class ClosedGroupPoller(
    private val scope: CoroutineScope,
    private val executor: CoroutineDispatcher,
    private val closedGroupSessionId: AccountId,
    private val configFactoryProtocol: ConfigFactoryProtocol,
    private val groupManagerV2: GroupManagerV2,
    private val storage: StorageProtocol,
    private val lokiApiDatabase: LokiAPIDatabaseProtocol,
    private val clock: SnodeClock,
) {
    companion object {
        private const val POLL_INTERVAL = 3_000L
        private const val POLL_ERROR_RETRY_DELAY = 10_000L

        private const val TAG = "ClosedGroupPoller"
    }

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return // already started, don't restart

        Log.d(TAG, "Starting closed group poller for ${closedGroupSessionId.hexString.take(4)}")
        job?.cancel()
        job = scope.launch(executor) {
            while (isActive) {
                try {
                    val swarmNodes = SnodeAPI.fetchSwarmNodes(closedGroupSessionId.hexString).toMutableSet()
                    var currentSnode: Snode? = null

                    while (isActive) {
                        if (currentSnode == null) {
                            check(swarmNodes.isNotEmpty()) { "No more swarm nodes found" }
                            Log.d(TAG, "No current snode, getting a new one. Remaining in pool = ${swarmNodes.size - 1}")
                            currentSnode = swarmNodes.random()
                            swarmNodes.remove(currentSnode)
                        }

                        val result = runCatching { poll(currentSnode!!) }
                        when {
                            result.isSuccess -> {
                                delay(POLL_INTERVAL)
                            }

                            result.isFailure -> {
                                val error = result.exceptionOrNull()!!
                                if (error is CancellationException) {
                                    throw error
                                }

                                Log.e(TAG, "Error polling closed group", error)
                                // Clearing snode so we get a new one next time
                                currentSnode = null
                                delay(POLL_INTERVAL)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error during group poller", e)
                    delay(POLL_ERROR_RETRY_DELAY)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun poll(snode: Snode): Unit = supervisorScope {
        val groupAuth =
            configFactoryProtocol.getGroupAuth(closedGroupSessionId) ?: return@supervisorScope
        val configHashesToExtends = configFactoryProtocol.withGroupConfigs(closedGroupSessionId) {
            buildSet {
                addAll(it.groupKeys.currentHashes())
                addAll(it.groupInfo.currentHashes())
                addAll(it.groupMembers.currentHashes())
            }
        }

        val adminKey = requireNotNull(configFactoryProtocol.getClosedGroup(closedGroupSessionId)) {
            "Group doesn't exist"
        }.adminKey

        val pollingTasks = mutableListOf<Pair<String, Deferred<*>>>()

        val receiveRevokeMessage = async {
            SnodeAPI.sendBatchRequest(
                snode,
                closedGroupSessionId.hexString,
                SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                    lastHash = lokiApiDatabase.getLastMessageHashValue(
                        snode,
                        closedGroupSessionId.hexString,
                        Namespace.REVOKED_GROUP_MESSAGES()
                    ).orEmpty(),
                    auth = groupAuth,
                    namespace = Namespace.REVOKED_GROUP_MESSAGES(),
                    maxSize = null,
                ),
                RetrieveMessageResponse::class.java
            ).messages.filterNotNull()
        }

        if (configHashesToExtends.isNotEmpty() && adminKey != null) {
            pollingTasks += "extending group config TTL" to async {
                SnodeAPI.sendBatchRequest(
                    snode,
                    closedGroupSessionId.hexString,
                    SnodeAPI.buildAuthenticatedAlterTtlBatchRequest(
                        messageHashes = configHashesToExtends.toList(),
                        auth = groupAuth,
                        newExpiry = clock.currentTimeMills() + 14.days.inWholeMilliseconds,
                        extend = true
                    ),
                )
            }
        }

        val groupMessageRetrieval = async {
            SnodeAPI.sendBatchRequest(
                snode = snode,
                publicKey = closedGroupSessionId.hexString,
                request = SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                    lastHash = lokiApiDatabase.getLastMessageHashValue(
                        snode,
                        closedGroupSessionId.hexString,
                        Namespace.CLOSED_GROUP_MESSAGES()
                    ).orEmpty(),
                    auth = groupAuth,
                    namespace = Namespace.CLOSED_GROUP_MESSAGES(),
                    maxSize = null,
                ),
                responseType = Map::class.java
            )
        }

        val groupConfigRetrieval = listOf(
            Namespace.ENCRYPTION_KEYS(),
            Namespace.CLOSED_GROUP_INFO(),
            Namespace.CLOSED_GROUP_MEMBERS()
        ).map { ns ->
            async {
                SnodeAPI.sendBatchRequest(
                    snode = snode,
                    publicKey = closedGroupSessionId.hexString,
                    request = SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                        lastHash = lokiApiDatabase.getLastMessageHashValue(
                            snode,
                            closedGroupSessionId.hexString,
                            ns
                        ).orEmpty(),
                        auth = groupAuth,
                        namespace = ns,
                        maxSize = null,
                    ),
                    responseType = RetrieveMessageResponse::class.java
                ).messages.filterNotNull()
            }
        }

        // The retrieval of the all group messages can be done concurrently,
        // however, in order for the messages to be able to be decrypted, the config messages
        // must be processed first.
        pollingTasks += "polling and handling group config keys and messages" to async {
            val result = runCatching {
                val (keysMessage, infoMessage, membersMessage) = groupConfigRetrieval.map { it.await() }
                handleGroupConfigMessages(keysMessage, infoMessage, membersMessage)
                saveLastMessageHash(snode, keysMessage, Namespace.ENCRYPTION_KEYS())
                saveLastMessageHash(snode, infoMessage, Namespace.CLOSED_GROUP_INFO())
                saveLastMessageHash(snode, membersMessage, Namespace.CLOSED_GROUP_MEMBERS())

                val regularMessages = groupMessageRetrieval.await()
                handleMessages(regularMessages, snode)
            }

            // Revoke message must be handled regardless, and at the end
            val revokedMessages = receiveRevokeMessage.await()
            handleRevoked(revokedMessages)
            saveLastMessageHash(snode, revokedMessages, Namespace.REVOKED_GROUP_MESSAGES())

            // Propagate any prior exceptions
            result.getOrThrow()
        }

        // Wait for all tasks to complete, gather any exceptions happened during polling
        val errors = pollingTasks.mapNotNull { (name, task) ->
            runCatching { task.await() }
                .exceptionOrNull()
                ?.takeIf { it !is CancellationException }
                ?.let { RuntimeException("Error $name", it) }
        }

        // If there were any errors, throw the first one and add the rest as "suppressed" exceptions
        if (errors.isNotEmpty()) {
            throw errors.first().apply {
                for (index in 1 until errors.size) {
                    addSuppressed(errors[index])
                }
            }
        }
    }

    private fun RetrieveMessageResponse.Message.toConfigMessage(): ConfigMessage {
        return ConfigMessage(hash, data, timestamp ?: clock.currentTimeMills())
    }

    private fun saveLastMessageHash(
        snode: Snode,
        messages: List<RetrieveMessageResponse.Message>,
        namespace: Int
    ) {
        if (messages.isNotEmpty()) {
            lokiApiDatabase.setLastMessageHashValue(
                snode = snode,
                publicKey = closedGroupSessionId.hexString,
                newValue = messages.last().hash,
                namespace = namespace
            )
        }
    }

    private suspend fun handleRevoked(messages: List<RetrieveMessageResponse.Message>) {
        messages.forEach { msg ->
            val decoded = configFactoryProtocol.decryptForUser(
                msg.data,
                Sodium.KICKED_DOMAIN,
                closedGroupSessionId,
            )

            if (decoded != null) {
                val message = decoded.decodeToString()
                val matcher = Sodium.KICKED_REGEX.matcher(message)
                if (matcher.matches()) {
                    val sessionId = matcher.group(1)
                    val messageGeneration = matcher.group(2)!!.toInt()
                    val currentKeysGeneration = configFactoryProtocol.withGroupConfigs(closedGroupSessionId) {
                        it.groupKeys.currentGeneration()
                    }

                    val isForMe = sessionId == storage.getUserPublicKey()
                    Log.d(TAG, "Received kicked message, for us? ${sessionId == storage.getUserPublicKey()}, message key generation = $messageGeneration, our key generation = $currentKeysGeneration")

                    if (isForMe && messageGeneration >= currentKeysGeneration) {
                        groupManagerV2.handleKicked(closedGroupSessionId)
                    }
                } else {
                    Log.w(TAG, "Received an invalid kicked message")
                }
            }
        }
    }

    private fun handleGroupConfigMessages(
        keysResponse: List<RetrieveMessageResponse.Message>,
        infoResponse: List<RetrieveMessageResponse.Message>,
        membersResponse: List<RetrieveMessageResponse.Message>
    ) {
        if (keysResponse.isEmpty() && infoResponse.isEmpty() && membersResponse.isEmpty()) {
            return
        }

        Log.d(
            TAG, "Handling group config messages(" +
                    "info = ${infoResponse.size}, " +
                    "keys = ${keysResponse.size}, " +
                    "members = ${membersResponse.size})"
        )

        configFactoryProtocol.mergeGroupConfigMessages(
            groupId = closedGroupSessionId,
            keys = keysResponse.map { it.toConfigMessage() },
            info = infoResponse.map { it.toConfigMessage() },
            members = membersResponse.map { it.toConfigMessage() },
        )
    }

    private fun handleMessages(body: RawResponse, snode: Snode) {
        val messages = configFactoryProtocol.withGroupConfigs(closedGroupSessionId) {
            SnodeAPI.parseRawMessagesResponse(
                rawResponse = body,
                snode = snode,
                publicKey = closedGroupSessionId.hexString,
                decrypt = it.groupKeys::decrypt,
                namespace = Namespace.CLOSED_GROUP_MESSAGES(),
            )
        }

        val parameters = messages.map { (envelope, serverHash) ->
            MessageReceiveParameters(
                envelope.toByteArray(),
                serverHash = serverHash,
                closedGroup = Destination.ClosedGroup(closedGroupSessionId.hexString)
            )
        }

        parameters.chunked(BatchMessageReceiveJob.BATCH_DEFAULT_NUMBER).forEach { chunk ->
            val job = BatchMessageReceiveJob(chunk)
            JobQueue.shared.add(job)
        }

        if (messages.isNotEmpty()) {
            Log.d(TAG, "Received and handled ${messages.size} group messages")
        }
    }
}