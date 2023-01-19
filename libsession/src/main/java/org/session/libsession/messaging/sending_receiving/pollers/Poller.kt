package org.session.libsession.messaging.sending_receiving.pollers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Log
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

    private fun processUserConfig(rawMessages: List<Pair<SignalServiceProtos.Envelope, String?>>) {

    }

    private fun poll(snode: Snode, deferred: Deferred<Unit, Exception>): Promise<Unit, Exception> {
        if (!hasStarted) { return Promise.ofFail(PromiseCanceledException()) }
        return task {
            runBlocking(Dispatchers.IO) {
                val requests = listOfNotNull(
                    // get messages
                    SnodeAPI.buildAuthenticatedRetrieveBatchRequest(snode, userPublicKey),
                    // get user config namespace
                    configFactory.userConfig?.let { currentUserConfig ->
                        SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                            snode,
                            userPublicKey,
                            currentUserConfig.configNamespace()
                        )
                    },
                    // get contact config namespace
                    configFactory.contacts?.let { currentContacts ->
                        SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                            snode,
                            userPublicKey,
                            currentContacts.configNamespace()
                        )
                    }
                )

                SnodeAPI.getRawBatchResponse(snode, userPublicKey, requests).bind { rawResponses ->
                    isCaughtUp = true
                    if (deferred.promise.isDone()) {
                        return@bind Promise.ofSuccess(Unit)
                    } else {
                        val messageResponse = (rawResponses["results"] as List<*>).first() as RawResponse
                        val messages = SnodeAPI.parseRawMessagesResponse(messageResponse, snode, userPublicKey)
                        val parameters = messages.map { (envelope, serverHash) ->
                            MessageReceiveParameters(envelope.toByteArray(), serverHash = serverHash)
                        }
                        parameters.chunked(BatchMessageReceiveJob.BATCH_DEFAULT_NUMBER).forEach { chunk ->
                            val job = BatchMessageReceiveJob(chunk)
                            JobQueue.shared.add(job)
                        }

                        poll(snode, deferred)
                    }
                }
            }
        }
    }
    // endregion
}
