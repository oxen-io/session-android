package org.session.libsession.messaging.sending_receiving.pollers

import android.util.SparseArray
import androidx.core.util.keyIterator
import androidx.core.util.valueIterator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import network.loki.messenger.libsession_util.ConfigBase
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
import org.session.libsession.snode.RawResponse
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeModule
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.session.libsignal.utilities.Snode
import java.security.SecureRandom
import java.util.Timer
import java.util.TimerTask

private class PromiseCanceledException : Exception("Promise canceled.")

class Poller(private val configFactory: ConfigFactoryProtocol) {
    var userPublicKey = MessagingModuleConfiguration.shared.storage.getUserPublicKey() ?: ""
    private var hasStarted: Boolean = false
    private val usedSnodes: MutableSet<Snode> = mutableSetOf()
    var isCaughtUp = false
    var configPollingJob: Job? = null

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

    private fun processUserConfig(snode: Snode, rawMessages: RawResponse) {
        SnodeAPI.parseRawMessagesResponse(rawMessages, snode, userPublicKey)
    }

    private fun processContactsConfig(snode: Snode, rawMessages: RawResponse) {

    }

    private fun processConvoVolatileConfig(snode: Snode, rawMessages: RawResponse) {

    }


    private fun poll(snode: Snode, deferred: Deferred<Unit, Exception>): Promise<Unit, Exception> {
        if (!hasStarted) { return Promise.ofFail(PromiseCanceledException()) }
        return task {
            runBlocking(Dispatchers.IO) {
                val requestSparseArray = SparseArray<SnodeAPI.SnodeBatchRequestInfo>()
                // get messages
                SnodeAPI.buildAuthenticatedRetrieveBatchRequest(snode, userPublicKey)!!.also { personalMessages ->
                    requestSparseArray[personalMessages.namespace] = personalMessages
                }
                // get the latest convo info volatile
                listOfNotNull(configFactory.user, configFactory.contacts, configFactory.convoVolatile).mapNotNull { config ->
                    SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                        snode, userPublicKey,
                        config.configNamespace()
                    )
                }.forEach { request ->
                    requestSparseArray[request.namespace] = request
                }

                val requests = requestSparseArray.valueIterator().asSequence().toList()

                SnodeAPI.getRawBatchResponse(snode, userPublicKey, requests).bind { rawResponses ->
                    isCaughtUp = true
                    if (deferred.promise.isDone()) {
                        return@bind Promise.ofSuccess(Unit)
                    } else {
                        Log.d("Loki-DBG", JsonUtil.toJson(rawResponses))
                        val requestList = (rawResponses["results"] as List<RawResponse>)
                        requestSparseArray.keyIterator().withIndex().forEach { (requestIndex, key) ->
                            requestList.getOrNull(requestIndex)?.let { rawResponse ->
                                if (key == Namespace.DEFAULT) {
                                    processPersonalMessages(snode, rawResponse)
                                } else {
                                    when (ConfigBase.kindFor(key)) {
                                        UserProfile ->
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
