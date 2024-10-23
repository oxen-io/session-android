@file:JvmName("PromiseUtilities")
package org.session.libsignal.utilities

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.functional.map

fun emptyPromise() = Promise.of(Unit)


fun <V, E : Throwable> Promise<V, E>.recover(callback: (exception: E) -> V): Promise<V, E> {
    val deferred = deferred<V, E>()
    success {
        deferred.resolve(it)
    }.fail {
        try {
            val value = callback(it)
            deferred.resolve(value)
        } catch (e: Throwable) {
            deferred.reject(it)
        }
    }
    return deferred.promise
}


infix fun <V, E: Exception> Promise<V, E>.sideEffect(
    callback: (value: V) -> Unit
) = map {
    callback(it)
    it
}
