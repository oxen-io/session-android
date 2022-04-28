package org.session.libsession.messaging.sending_receiving.pollers

import nl.komponents.kovenant.*
import nl.komponents.kovenant.functional.bind
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.BatchMessageReceiveJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveParameters
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeModule
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Snode
import java.security.SecureRandom
import java.util.*

private class PromiseCanceledException : Exception("Promise canceled.")

class Poller {
    var userPublicKey = MessagingModuleConfiguration.shared.storage.getUserPublicKey() ?: ""
    private var hasStarted: Boolean = false
    private val usedSnodes: MutableSet<Snode> = mutableSetOf()
    var isCaughtUp = false

    // region Settings
    companion object {
        private val retryInterval: Long = 1 * 1000
    }
    // endregion

    // region Public API
    fun startIfNeeded() {
        if (hasStarted) { return }
        Log.d("Loki", "Started polling.")
        hasStarted = true
        setUpPolling()
    }

    fun stopIfNeeded() {
        Log.d("Loki", "Stopped polling.")
        hasStarted = false
        usedSnodes.clear()
    }
    // endregion

    // region Private API
    private fun setUpPolling() {
        if (!hasStarted) { return; }
        val thread = Thread.currentThread()
        SnodeAPI.getSwarm(userPublicKey).bind {
            usedSnodes.clear()
            val deferred = deferred<Unit, Exception>()
            pollNextSnode(deferred)
            deferred.promise
        }.always {
            Timer().schedule(object : TimerTask() {

                override fun run() {
                    thread.run { setUpPolling() }
                }
            }, retryInterval)
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

    private fun poll(snode: Snode, deferred: Deferred<Unit, Exception>): Promise<Unit, Exception> {
        if (!hasStarted) { return Promise.ofFail(PromiseCanceledException()) }
        return SnodeAPI.getRawMessages(snode, userPublicKey).bind { rawResponse ->
            isCaughtUp = true
            if (deferred.promise.isDone()) {
                task { Unit } // The long polling connection has been canceled; don't recurse
            } else {
                val messages = SnodeAPI.parseRawMessagesResponse(rawResponse, snode, userPublicKey)
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
    // endregion
}
