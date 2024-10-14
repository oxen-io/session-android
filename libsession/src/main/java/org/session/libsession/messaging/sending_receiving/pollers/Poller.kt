package org.session.libsession.messaging.sending_receiving.pollers

import android.util.SparseArray
import androidx.core.util.valueIterator
import kotlinx.coroutines.GlobalScope
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.resolve
import org.session.libsession.database.StorageProtocol
import org.session.libsession.database.userAuth
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.BatchMessageReceiveJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveParameters
import org.session.libsession.snode.RawResponse
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeModule
import org.session.libsession.snode.utilities.asyncPromise
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.ConfigMessage
import org.session.libsession.utilities.UserConfigType
import org.session.libsignal.database.LokiAPIDatabaseProtocol
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.session.libsignal.utilities.Snode
import org.session.libsignal.utilities.Util.SECURE_RANDOM
import java.util.Timer
import java.util.TimerTask
import kotlin.time.Duration.Companion.days

private const val TAG = "Poller"

private class PromiseCanceledException : Exception("Promise canceled.")

class Poller(
    private val configFactory: ConfigFactoryProtocol,
    private val storage: StorageProtocol,
    private val lokiApiDatabase: LokiAPIDatabaseProtocol
) {
    private val userPublicKey: String
        get() = storage.getUserPublicKey().orEmpty()

    private var hasStarted: Boolean = false
    private val usedSnodes: MutableSet<Snode> = mutableSetOf()
    var isCaughtUp = false

    // region Settings
    companion object {
        private const val retryInterval: Long = 2 * 1000
        private const val maxInterval: Long = 15 * 1000
    }
    // endregion

    // region Public API
    fun startIfNeeded() {
        if (hasStarted) { return }
        Log.d(TAG, "Started polling.")
        hasStarted = true
        setUpPolling(retryInterval)
    }

    fun stopIfNeeded() {
        Log.d(TAG, "Stopped polling.")
        hasStarted = false
        usedSnodes.clear()
    }

    fun retrieveUserProfile() {
        Log.d(TAG, "Retrieving user profile. for key = $userPublicKey")
        SnodeAPI.getSwarm(userPublicKey).bind {
            usedSnodes.clear()
            deferred<Unit, Exception>().also {
                pollNextSnode(userProfileOnly = true, it)
            }.promise
        }.fail {
            Log.e(TAG, "Failed to retrieve user profile.", it)
        }
    }
    // endregion

    // region Private API
    private fun setUpPolling(delay: Long) {
        if (!hasStarted) { return; }
        val thread = Thread.currentThread()
        SnodeAPI.getSwarm(userPublicKey).bind {
            usedSnodes.clear()
            val deferred = deferred<Unit, Exception>()
            pollNextSnode(deferred = deferred)
            deferred.promise
        }.success {
            val nextDelay = if (isCaughtUp) retryInterval else 0
            Timer().schedule(object : TimerTask() {
                override fun run() {
                    thread.run { setUpPolling(retryInterval) }
                }
            }, nextDelay)
        }.fail {
            val nextDelay = minOf(maxInterval, (delay * 1.2).toLong())
            Timer().schedule(object : TimerTask() {
                override fun run() {
                    thread.run { setUpPolling(nextDelay) }
                }
            }, nextDelay)
        }
    }

    private fun pollNextSnode(userProfileOnly: Boolean = false, deferred: Deferred<Unit, Exception>) {
        val swarm = SnodeModule.shared.storage.getSwarm(userPublicKey) ?: setOf()
        val unusedSnodes = swarm.subtract(usedSnodes)
        if (unusedSnodes.isNotEmpty()) {
            val index = SECURE_RANDOM.nextInt(unusedSnodes.size)
            val nextSnode = unusedSnodes.elementAt(index)
            usedSnodes.add(nextSnode)
            Log.d(TAG, "Polling $nextSnode.")
            poll(userProfileOnly, nextSnode, deferred).fail { exception ->
                if (exception is PromiseCanceledException) {
                    Log.d(TAG, "Polling $nextSnode canceled.")
                } else {
                    Log.d(TAG, "Polling $nextSnode failed; dropping it and switching to next snode.")
                    SnodeAPI.dropSnodeFromSwarmIfNeeded(nextSnode, userPublicKey)
                    pollNextSnode(userProfileOnly, deferred)
                }
            }
        } else {
            isCaughtUp = true
            deferred.resolve()
        }
    }

    private fun processPersonalMessages(snode: Snode, rawMessages: RawResponse) {
        val messages = SnodeAPI.parseRawMessagesResponse(rawMessages, snode, userPublicKey)
        val parameters = messages.map { (envelope, serverHash) ->
            MessageReceiveParameters(envelope.toByteArray(), serverHash = serverHash)
        }
        parameters.chunked(BatchMessageReceiveJob.BATCH_DEFAULT_NUMBER).forEach { chunk ->
            val job = BatchMessageReceiveJob(chunk)
            JobQueue.shared.add(job)
        }
    }

    private fun processConfig(snode: Snode, rawMessages: RawResponse, forConfig: UserConfigType) {
        val messages = rawMessages["messages"] as? List<*>
        val namespace = forConfig.namespace
        val processed = if (!messages.isNullOrEmpty()) {
            SnodeAPI.updateLastMessageHashValueIfPossible(snode, userPublicKey, messages, namespace)
            SnodeAPI.removeDuplicates(userPublicKey, messages, namespace, true).mapNotNull { rawMessageAsJSON ->
                val hashValue = rawMessageAsJSON["hash"] as? String ?: return@mapNotNull null
                val b64EncodedBody = rawMessageAsJSON["data"] as? String ?: return@mapNotNull null
                val timestamp = rawMessageAsJSON["t"] as? Long ?: SnodeAPI.nowWithOffset
                val body = Base64.decode(b64EncodedBody)
                ConfigMessage(data = body, hash = hashValue, timestamp = timestamp)
            }
        } else emptyList()

        if (processed.isEmpty()) return

        Log.i(TAG, "Processing ${processed.size} messages for $forConfig")

        try {
            configFactory.mergeUserConfigs(
                userConfigType = forConfig,
                messages = processed,
            )
        } catch (e: Exception) {
            Log.e(TAG, e)
        }
    }

    private fun poll(userProfileOnly: Boolean, snode: Snode, deferred: Deferred<Unit, Exception>): Promise<Unit, Exception> {
        if (userProfileOnly) {
            return pollUserProfile(snode, deferred)
        }
        return poll(snode, deferred)
    }

    private fun pollUserProfile(snode: Snode, deferred: Deferred<Unit, Exception>): Promise<Unit, Exception> = GlobalScope.asyncPromise {
        val requests = mutableListOf<SnodeAPI.SnodeBatchRequestInfo>()
        val hashesToExtend = mutableSetOf<String>()
        val userAuth = requireNotNull(MessagingModuleConfiguration.shared.storage.userAuth)

        configFactory.withUserConfigs {
            hashesToExtend += it.userProfile.currentHashes()
        }

        requests += SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
            lastHash = lokiApiDatabase.getLastMessageHashValue(
                snode = snode,
                publicKey = userAuth.accountId.hexString,
                namespace = Namespace.USER_PROFILE()
            ),
            auth = userAuth,
            namespace = Namespace.USER_PROFILE(),
            maxSize = -8
        )

        if (hashesToExtend.isNotEmpty()) {
            SnodeAPI.buildAuthenticatedAlterTtlBatchRequest(
                    messageHashes = hashesToExtend.toList(),
                    auth = userAuth,
                    newExpiry = SnodeAPI.nowWithOffset + 14.days.inWholeMilliseconds,
                    extend = true
            ).let { extensionRequest ->
                requests += extensionRequest
            }
        }

        if (requests.isNotEmpty()) {
            SnodeAPI.getRawBatchResponse(snode, userPublicKey, requests).bind { rawResponses ->
                isCaughtUp = true
                if (!deferred.promise.isDone()) {
                    val responseList = (rawResponses["results"] as List<RawResponse>)
                    responseList.getOrNull(0)?.let { rawResponse ->
                        if (rawResponse["code"] as? Int != 200) {
                            Log.e(TAG, "Batch sub-request had non-200 response code, returned code ${(rawResponse["code"] as? Int) ?: "[unknown]"}")
                        } else {
                            val body = rawResponse["body"] as? RawResponse
                            if (body == null) {
                                Log.e(TAG, "Batch sub-request didn't contain a body")
                            } else {
                                processConfig(snode, body, UserConfigType.USER_PROFILE)
                            }
                        }
                    }
                }
                Promise.ofSuccess(Unit)
            }.fail {
                Log.e(TAG, "Failed to get raw batch response", it)
            }
        }
    }


    private fun poll(snode: Snode, deferred: Deferred<Unit, Exception>): Promise<Unit, Exception> {
        if (!hasStarted) { return Promise.ofFail(PromiseCanceledException()) }
        return GlobalScope.asyncPromise {
            val userAuth = requireNotNull(MessagingModuleConfiguration.shared.storage.userAuth)
            val requestSparseArray = SparseArray<SnodeAPI.SnodeBatchRequestInfo>()
            // get messages
            SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                lastHash = lokiApiDatabase.getLastMessageHashValue(
                    snode = snode,
                    publicKey = userAuth.accountId.hexString,
                    namespace = Namespace.DEFAULT()
                ),
                auth = userAuth,
                maxSize = -2)
                .also { personalMessages ->
                // namespaces here should always be set
                requestSparseArray[personalMessages.namespace!!] = personalMessages
            }
            // get the latest convo info volatile
            val hashesToExtend = mutableSetOf<String>()
            configFactory.withUserConfigs { configs ->
                UserConfigType
                    .entries
                    .map { type ->
                        val config = configs.getConfig(type)
                        hashesToExtend += config.currentHashes()
                        type.namespace to SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                            lastHash = lokiApiDatabase.getLastMessageHashValue(
                                snode = snode,
                                publicKey = userAuth.accountId.hexString,
                                namespace = type.namespace
                            ),
                            auth = userAuth,
                            namespace = type.namespace,
                            maxSize = -8
                        )
                    }
            }.forEach { (namespace, request) ->
                // namespaces here should always be set
                requestSparseArray[namespace] = request
            }

            val requests =
                requestSparseArray.valueIterator().asSequence().toMutableList()

            if (hashesToExtend.isNotEmpty()) {
                SnodeAPI.buildAuthenticatedAlterTtlBatchRequest(
                    messageHashes = hashesToExtend.toList(),
                    auth = userAuth,
                    newExpiry = SnodeAPI.nowWithOffset + 14.days.inWholeMilliseconds,
                    extend = true
                ).let { extensionRequest ->
                    requests += extensionRequest
                }
            }

            if (requests.isNotEmpty()) {
                SnodeAPI.getRawBatchResponse(snode, userPublicKey, requests).bind { rawResponses ->
                    isCaughtUp = true
                    if (deferred.promise.isDone()) {
                        return@bind Promise.ofSuccess(Unit)
                    } else {
                        val responseList = (rawResponses["results"] as List<RawResponse>)
                        // in case we had null configs, the array won't be fully populated
                        // index of the sparse array key iterator should be the request index, with the key being the namespace
                        UserConfigType.entries
                            .map { type -> type to requestSparseArray.indexOfKey(type.namespace) }
                            .filter { (_, i) -> i >= 0 }
                            .forEach { (configType, requestIndex) ->
                                responseList.getOrNull(requestIndex)?.let { rawResponse ->
                                    if (rawResponse["code"] as? Int != 200) {
                                        Log.e(TAG, "Batch sub-request had non-200 response code, returned code ${(rawResponse["code"] as? Int) ?: "[unknown]"}")
                                        return@forEach
                                    }
                                    val body = rawResponse["body"] as? RawResponse
                                    if (body == null) {
                                        Log.e(TAG, "Batch sub-request didn't contain a body")
                                        return@forEach
                                    }

                                    processConfig(snode, body, configType)
                                }
                            }

                        // the first response will be the personal messages (we want these to be processed after config messages)
                        val personalResponseIndex = requestSparseArray.indexOfKey(Namespace.DEFAULT())
                        if (personalResponseIndex >= 0) {
                            responseList.getOrNull(personalResponseIndex)?.let { rawResponse ->
                                if (rawResponse["code"] as? Int != 200) {
                                    Log.e(TAG, "Batch sub-request for personal messages had non-200 response code, returned code ${(rawResponse["code"] as? Int) ?: "[unknown]"}")
                                    // If we got a non-success response then the snode might be bad so we should try rotate
                                    // to a different one just in case
                                    pollNextSnode(deferred = deferred)
                                    return@bind Promise.ofSuccess(Unit)
                                } else {
                                    val body = rawResponse["body"] as? RawResponse
                                    if (body == null) {
                                        Log.e(TAG, "Batch sub-request for personal messages didn't contain a body")
                                    } else {
                                        processPersonalMessages(snode, body)
                                    }
                                }
                            }
                        }

                        poll(snode, deferred)
                    }
                }.fail {
                    Log.e(TAG, "Failed to get raw batch response", it)
                    poll(snode, deferred)
                }
            }
        }
    }
    // endregion
}
