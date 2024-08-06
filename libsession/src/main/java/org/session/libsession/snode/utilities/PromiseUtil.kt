package org.session.libsession.snode.utilities

import nl.komponents.kovenant.Promise
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun <T, E: Throwable> Promise<T, E>.await(): T {
    return suspendCoroutine { cont ->
        success(cont::resume)
        fail(cont::resumeWithException)
    }
}