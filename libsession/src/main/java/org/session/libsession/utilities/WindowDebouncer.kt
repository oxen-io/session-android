package org.session.libsession.utilities

import android.os.Handler
import java.util.concurrent.atomic.AtomicReference

/**
 * Not really a 'debouncer' but named to be similar to the current Debouncer
 * designed to queue tasks on a window (if not already queued) like a timer
 */
class WindowDebouncer(private val handler: Handler, private val window: Long) {

    private val atomicRef: AtomicReference<Runnable?> = AtomicReference(null)

    private val recursiveRunnable = {
        val runnable = atomicRef.getAndSet(null)
        runnable?.run()
        recurse()
    }

    fun publish(runnable: Runnable) {
        atomicRef.compareAndSet(null, runnable)
    }

    private fun recurse() {
        handler.postDelayed(recursiveRunnable, window)
    }

    init {
        recurse()
    }

}