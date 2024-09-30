package org.session.libsignal.utilities

import kotlinx.coroutines.delay
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.session.libsignal.exceptions.NonRetryableException
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

fun <V, T : Promise<V, Exception>> retryIfNeeded(maxRetryCount: Int, retryInterval: Long = 1000L, body: () -> T): Promise<V, Exception> {
    var retryCount = 0
    val deferred = deferred<V, Exception>()
    val thread = Thread.currentThread()
    fun retryIfNeeded() {
        body().success {
            deferred.resolve(it)
        }.fail {
            if (retryCount == maxRetryCount) {
                deferred.reject(it)
            } else {
                retryCount += 1
                Timer().schedule(object : TimerTask() {

                    override fun run() {
                        thread.run { retryIfNeeded() }
                    }
                }, retryInterval)
            }
        }
    }
    retryIfNeeded()
    return deferred.promise
}

suspend fun <T> retryWithUniformInterval(maxRetryCount: Int = 3, retryIntervalMills: Long = 1000L, body: suspend () -> T): T {
    var retryCount = 0
    while (true) {
        try {
            return body()
        } catch (e: CancellationException) {
            throw e
        } catch (e: NonRetryableException) {
            throw e
        } catch (e: Exception) {
            if (retryCount == maxRetryCount) {
                throw e
            } else {
                retryCount += 1
                delay(retryIntervalMills)
            }
        }
    }
}
