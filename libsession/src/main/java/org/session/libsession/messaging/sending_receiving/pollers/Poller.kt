package org.session.libsession.messaging.sending_receiving.pollers

import android.util.SparseArray
import androidx.core.util.keyIterator
import androidx.core.util.valueIterator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import network.loki.messenger.libsession_util.ConfigBase
import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.ConversationVolatileConfig
import network.loki.messenger.libsession_util.UserProfile
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.resolve
import nl.komponents.kovenant.task
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.BatchMessageReceiveJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveParameters
import org.session.libsession.messaging.messages.control.SharedConfigurationMessage
import org.session.libsession.messaging.sending_receiving.MessageReceiver
import org.session.libsession.snode.RawResponse
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeModule
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.WindowDebouncer
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.session.libsignal.utilities.Snode
import java.security.SecureRandom
import java.util.Timer
import java.util.TimerTask

private class PromiseCanceledException : Exception("Promise canceled.")

class Poller(private val configFactory: ConfigFactoryProtocol, debounceTimer: Timer) {
    var userPublicKey = MessagingModuleConfiguration.shared.storage.getUserPublicKey() ?: ""
    private var hasStarted: Boolean = false
    private val usedSnodes: MutableSet<Snode> = mutableSetOf()
    var isCaughtUp = false
    var configPollingJob: Job? = null
    
    val configDebouncer = WindowDebouncer(3000, debounceTimer)

    // region Settings
    companion object {
        private const val retryInterval: Long = 2 * 1000
        private const val maxInterval: Long = 15 * 1000
    }
    // endregion

    // region Public API
    fun startIfNeeded() {
        if (hasStarted) { return }
        Log.d("Loki", "Started polling.")
        hasStarted = true
        setUpPolling(retryInterval)
    }

    fun stopIfNeeded() {
        Log.d("Loki", "Stopped polling.")
        hasStarted = false
        usedSnodes.clear()
    }
    // endregion

    // region Private API
    private fun setUpPolling(delay: Long) {
        if (!hasStarted) { return; }
        val thread = Thread.currentThread()
        SnodeAPI.getSwarm(userPublicKey).bind {
            usedSnodes.clear()
            val deferred = deferred<Unit, Exception>()
            pollNextSnode(deferred)
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

    private fun pollNextSnode(deferred: Deferred<Unit, Exception>) {
        val swarm = SnodeModule.shared.storage.getSwarm(userPublicKey) ?: setOf()
        val unusedSnodes = swarm.subtract(usedSnodes)
        if (unusedSnodes.isNotEmpty()) {
            val index = SecureRandom().nextInt(unusedSnodes.size)
            val nextSnode = unusedSnodes.elementAt(index)
            usedSnodes.add(nextSnode)
            Log.d("Loki", "Polling $nextSnode.")
            poll(nextSnode, deferred).fail { exception ->
                if (exception is PromiseCanceledException) {
                    Log.d("Loki", "Polling $nextSnode canceled.")
                } else {
                    Log.d("Loki", "Polling $nextSnode failed; dropping it and switching to next snode.")
                    SnodeAPI.dropSnodeFromSwarmIfNeeded(nextSnode, userPublicKey)
                    pollNextSnode(deferred)
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

    private fun processConfig(snode: Snode, rawMessages: RawResponse, namespace: Int, forConfigObject: ConfigBase?) {
        if (forConfigObject == null) return

        val messages = SnodeAPI.parseRawMessagesResponse(
            rawMessages,
            snode,
            userPublicKey,
            namespace,
            updateLatestHash = false,
            updateStoredHashes = false,
        ).filter { (_, hash) -> !configFactory.getHashesFor(forConfigObject).contains(hash) }

        if (messages.isEmpty()) {
            // no new messages to process
            return
        }

        Log.d("Loki-DBG", "Received configs with hashes: ${messages.map { it.second }}")
        Log.d("Loki-DBG", "Hashes we have for config: ${configFactory.getHashesFor(forConfigObject)}")

        messages.forEach { (envelope, hash) ->
            try {
                val (message, _) = MessageReceiver.parse(data = envelope.toByteArray(), openGroupServerID = null)
                // sanity checks
                if (message !is SharedConfigurationMessage) {
                    Log.w("Loki-DBG", "shared config message handled in configs wasn't SharedConfigurationMessage but was ${message.javaClass.simpleName}")
                    return@forEach
                }
                Log.d("Loki-DBG", "Merging config of kind ${message.kind} into ${forConfigObject.javaClass.simpleName}")
                forConfigObject.merge(message.data)
                configFactory.appendHash(forConfigObject, hash!!)
            } catch (e: Exception) {
                Log.e("Loki", e)
            }
        }
        // process new results
        configFactory.persist(forConfigObject)
    }

    private fun poll(snode: Snode, deferred: Deferred<Unit, Exception>): Promise<Unit, Exception> {
        if (!hasStarted) { return Promise.ofFail(PromiseCanceledException()) }
        return task {
            runBlocking(Dispatchers.IO) {
                val requestSparseArray = SparseArray<SnodeAPI.SnodeBatchRequestInfo>()
                // get messages
                SnodeAPI.buildAuthenticatedRetrieveBatchRequest(snode, userPublicKey)!!.also { personalMessages ->
                    // namespaces here should always be set
                    requestSparseArray[personalMessages.namespace!!] = personalMessages
                }
                // get the latest convo info volatile
                listOfNotNull(configFactory.user, configFactory.contacts, configFactory.convoVolatile).mapNotNull { config ->
                    SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                        snode, userPublicKey,
                        config.configNamespace()
                    )
                }.forEach { request ->
                    // namespaces here should always be set
                    requestSparseArray[request.namespace!!] = request
                }

                if (requestSparseArray.size() == 1) {
                    // only one (the personal messages)
                    Log.d("Loki-DBG", "Not building requests for the configs, current config state:")
                    Log.d("Loki-DBG", "${listOf(configFactory.user, configFactory.contacts, configFactory.convoVolatile)}")
                }

                val requests = requestSparseArray.valueIterator().asSequence().toList()

                SnodeAPI.getRawBatchResponse(snode, userPublicKey, requests).bind { rawResponses ->
                    isCaughtUp = true
                    if (deferred.promise.isDone()) {
                        return@bind Promise.ofSuccess(Unit)
                    } else {
                        val responseList = (rawResponses["results"] as List<RawResponse>)
                        // the first response will be the personal messages
                        val personalResponseIndex = requestSparseArray.indexOfKey(Namespace.DEFAULT)
                        if (personalResponseIndex >= 0) {
                            responseList.getOrNull(personalResponseIndex)?.let { rawResponse ->
                                if (rawResponse["code"] as? Int != 200) {
                                    Log.e("Loki-DBG", "Batch sub-request for personal messages had non-200 response code, returned code ${(rawResponse["code"] as? Int) ?: "[unknown]"}")
                                } else {
                                    val body = rawResponse["body"] as? RawResponse
                                    if (body == null) {
                                        Log.e("Loki-DBG", "Batch sub-request for personal messages didn't contain a body")
                                    } else {
                                        processPersonalMessages(snode, body)
                                    }
                                }
                            }
                        }
                        // in case we had null configs, the array won't be fully populated
                        // index of the sparse array key iterator should be the request index, with the key being the namespace
                        configDebouncer.publish {
                            requestSparseArray.keyIterator().withIndex().forEach { (requestIndex, key) ->
                                responseList.getOrNull(requestIndex)?.let { rawResponse ->
                                    if (rawResponse["code"] as? Int != 200) {
                                        Log.e("Loki-DBG", "Batch sub-request had non-200 response code, returned code ${(rawResponse["code"] as? Int) ?: "[unknown]"}")
                                        return@forEach
                                    }
                                    val body = rawResponse["body"] as? RawResponse
                                    if (body == null) {
                                        Log.e("Loki-DBG", "Batch sub-request didn't contain a body")
                                        return@forEach
                                    }
                                    if (key == Namespace.DEFAULT) {
                                        return@forEach // continue, skip default namespace
                                    } else {
                                        when (ConfigBase.kindFor(key)) {
                                            UserProfile::class.java -> processConfig(snode, body, key, configFactory.user)
                                            Contacts::class.java -> processConfig(snode, body, key, configFactory.contacts)
                                            ConversationVolatileConfig::class.java -> processConfig(snode, body, key, configFactory.convoVolatile)
                                        }
                                    }
                                }
                            }
                        }
                        poll(snode, deferred)
                    }
                }
            }
        }
    }
    // endregion
}
