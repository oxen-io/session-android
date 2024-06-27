package org.thoughtcrime.securesms

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import nl.komponents.kovenant.Kovenant
import nl.komponents.kovenant.jvm.asDispatcher
import org.session.libsignal.utilities.Log
import java.util.concurrent.Executors

object AppContext {

    fun configureKovenant() {
        Kovenant.context {
            callbackContext.dispatcher = Executors.newSingleThreadExecutor().asDispatcher()
            workerContext.dispatcher = Dispatchers.IO.asExecutor().asDispatcher()
            multipleCompletion = { v1, v2 ->
                Log.d("Loki", "Promise resolved more than once (first with $v1, then with $v2); ignoring $v2.")
            }
        }
    }
}