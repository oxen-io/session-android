package org.session.libsession.messaging.groups

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsignal.utilities.Log

private const val TAG = "RemoveGroupMemberHandler"

private const val MIN_PROCESSING_INTERVAL_MILLS = 1_000L

class RemoveGroupMemberHandler(
    private val configFactory: ConfigFactoryProtocol,
    scope: CoroutineScope = GlobalScope
) {
    init {
        scope.launch {
            val processStarted = SystemClock.uptimeMillis()

            try {
                processPendingMemberRemoval()
            } catch (e: Exception) {
                Log.e(TAG, "Error processing pending member removal", e)
            }

            configFactory.configUpdateNotifications.firstOrNull()

            // Make sure we don't process too often. As some of the config changes don't apply
            // to us, but we have no way to tell if it does or not. The safest way is to process
            // everytime any config changes, with a minimum interval.
            val delayMills = MIN_PROCESSING_INTERVAL_MILLS - (SystemClock.uptimeMillis() - processStarted)
            if (delayMills > 0) {
                delay(delayMills)
            }
        }
    }

    private suspend fun processPendingMemberRemoval() {
        val userGroups = checkNotNull(configFactory.userGroups) {
            "User groups config is null"
        }


    }
}