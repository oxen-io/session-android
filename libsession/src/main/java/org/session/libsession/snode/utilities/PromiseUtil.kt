package org.session.libsession.snode.utilities

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.session.libsignal.utilities.Log
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend inline fun <T, E: Throwable> Promise<T, E>.await(): T {
    return suspendCoroutine { cont ->
        success(cont::resume)
        fail(cont::resumeWithException)
    }
}

fun <V, E: Throwable> Promise<V, E>.successBackground(callback: (value: V) -> Unit): Promise<V, E> {
    GlobalScope.launch {
        try {
            callback(this@successBackground.await())
        } catch (e: Exception) {
            Log.d("Loki", "Failed to execute task in background: ${e.message}.")
        }
    }
    return this
}

fun <T> CoroutineScope.asyncPromise(context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> T): Promise<T, Exception> {
    val defer = deferred<T, Exception>()
    launch(context) {
        try {
            defer.resolve(block())
        } catch (e: Exception) {
            defer.reject(e)
        }
    }

    return defer.promise
}

fun <T> CoroutineScope.retrySuspendAsPromise(
    maxRetryCount: Int,
    retryIntervalMills: Long = 1_000L,
    body: suspend () -> T
): Promise<T, Exception> {
    return asyncPromise {
        var retryCount = 0
        while (true) {
            try {
                return@asyncPromise body()
            } catch (e: CancellationException) {
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

        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException("Unreachable code")
    }
}