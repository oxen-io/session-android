package org.thoughtcrime.securesms.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.all
import nl.komponents.kovenant.functional.bind
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.BatchMessageReceiveJob
import org.session.libsession.messaging.jobs.MessageReceiveParameters
import org.session.libsession.messaging.sending_receiving.pollers.ClosedGroupPollerV2
import org.session.libsession.messaging.sending_receiving.pollers.OpenGroupPoller
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import java.util.concurrent.TimeUnit

class BackgroundPollWorker(val context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val TAG = "BackgroundPollWorker"

        @JvmStatic
        fun schedulePeriodic(context: Context) {
            Log.v(TAG, "Scheduling periodic work.")
            val builder = PeriodicWorkRequestBuilder<BackgroundPollWorker>(15, TimeUnit.MINUTES)
            builder.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            val workRequest = builder.build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    override fun doWork(): Result {
        if (TextSecurePreferences.getLocalNumber(context) == null) {
            Log.v(TAG, "User not registered yet.")
            return Result.failure()
        }

        try {
            Log.v(TAG, "Performing background poll.")
            val promises = mutableListOf<Promise<Unit, Exception>>()

            // DMs
            val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
            val dmsPromise = SnodeAPI.getMessages(userPublicKey).bind { envelopes ->
                val params = envelopes.map { (envelope, serverHash) ->
                    // FIXME: Using a job here seems like a bad idea...
                    MessageReceiveParameters(envelope.toByteArray(), serverHash, null)
                }
                BatchMessageReceiveJob(params).executeAsync()
            }
            promises.add(dmsPromise)

            // Closed groups
            val closedGroupPoller = ClosedGroupPollerV2() // Intentionally don't use shared
            val storage = MessagingModuleConfiguration.shared.storage
            val allGroupPublicKeys = storage.getAllClosedGroupPublicKeys()
            allGroupPublicKeys.iterator().forEach { closedGroupPoller.poll(it) }

            // Open Groups
            val threadDB = DatabaseComponent.get(context).lokiThreadDatabase()
            val openGroups = threadDB.getAllOpenGroups()
            val openGroupServers = openGroups.map { it.value.server }.toSet()

            for (server in openGroupServers) {
                val poller = OpenGroupPoller(server, null)
                poller.hasStarted = true
                promises.add(poller.poll())
            }

            // Wait until all the promises are resolved
            all(promises).get()

            return Result.success()
        } catch (exception: Exception) {
            Log.e(TAG, "Background poll failed due to error: ${exception.message}.", exception)
            return Result.retry()
        }
    }

     class BootBroadcastReceiver: BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                Log.v(TAG, "Boot broadcast caught.")
                schedulePeriodic(context)
            }
        }
    }
}
