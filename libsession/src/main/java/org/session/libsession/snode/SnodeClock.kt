package org.session.libsession.snode

import android.os.SystemClock
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.session.libsession.snode.utilities.await
import org.session.libsignal.utilities.Log
import java.util.Date

/**
 * A class that manages the network time by querying the network time from a random snode. The
 * primary goal of this class is to provide a time that is not tied to current system time and not
 * prone to time changes locally.
 *
 * Before the first network query is successfully, calling [currentTimeMills] will return the current
 * system time.
 */
class SnodeClock() {
    private val instantState = MutableStateFlow<Instant?>(null)
    private var job: Job? = null

    fun start() {
        require(job == null) { "Already started" }

        job = GlobalScope.launch {
            while (true) {
                try {
                    val node = SnodeAPI.getRandomSnode().await()
                    val requestStarted = SystemClock.uptimeMillis()

                    var networkTime = SnodeAPI.getNetworkTime(node).await().second
                    val requestEnded = SystemClock.uptimeMillis()

                    // Adjust the network time to account for the time it took to make the request
                    // so that the network time equals to the time when the request was started
                    networkTime -= (requestEnded - requestStarted) / 2

                    val inst = Instant(requestStarted, networkTime)

                    Log.d("SnodeClock", "Network time: ${Date(inst.now())}, system time: ${Date()}")

                    instantState.value = inst
                } catch (e: Exception) {
                    Log.e("SnodeClock", "Failed to get network time. Retrying in a few seconds", e)
                } finally {
                    // Retry frequently if we haven't got any result before
                    val delayMills = if (instantState.value == null) {
                        3_000L
                    } else {
                        3600_000L
                    }

                    delay(delayMills)
                }
            }
        }
    }

    /**
     * Wait for the network adjusted time to come through.
     */
    suspend fun waitForNetworkAdjustedTime(): Long {
        return instantState.filterNotNull().first().now()
    }

    /**
     * Get the current time in milliseconds. If the network time is not available yet, this method
     * will return the current system time.
     */
    fun currentTimeMills(): Long {
        return instantState.value?.now() ?: System.currentTimeMillis()
    }

    private class Instant(
        val systemUptime: Long,
        val networkTime: Long,
    ) {
        fun now(): Long {
            val elapsed = SystemClock.uptimeMillis() - systemUptime
            return networkTime + elapsed
        }
    }
}