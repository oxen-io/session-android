package org.thoughtcrime.securesms.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
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
import org.session.libsignal.utilities.recover
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import java.util.concurrent.TimeUnit

class BackgroundPollWorker(val context: Context, params: WorkerParameters) : Worker(context, params) {
    enum class Targets {
        DMS, CLOSED_GROUPS, OPEN_GROUPS
    }

    companion object {
        const val TAG = "BackgroundPollWorker"
        const val INITIAL_SCHEDULE_TIME = "INITIAL_SCHEDULE_TIME"
        const val REQUEST_TARGETS = "REQUEST_TARGETS"

        @JvmStatic
        fun schedulePeriodic(context: Context) = schedulePeriodic(context, targets = Targets.values())

        @JvmStatic
        fun schedulePeriodic(context: Context, targets: Array<Targets>) {
            Log.v(TAG, "Scheduling periodic work.")
            val durationMinutes: Long = 15
            val builder = PeriodicWorkRequestBuilder<BackgroundPollWorker>(durationMinutes, TimeUnit.MINUTES)
            builder.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())

            val dataBuilder = Data.Builder()
            dataBuilder.putLong(INITIAL_SCHEDULE_TIME, System.currentTimeMillis() + (durationMinutes * 60 * 1000))
            dataBuilder.putStringArray(REQUEST_TARGETS, targets.map { it.name }.toTypedArray())
            builder.setInputData(dataBuilder.build())

            val workRequest = builder.build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }

        @JvmStatic
        fun scheduleOnce(context: Context, targets: Array<Targets> = Targets.values()) {
            Log.v(TAG, "Scheduling single run.")
            val builder = OneTimeWorkRequestBuilder<BackgroundPollWorker>()
            builder.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())

            val dataBuilder = Data.Builder()
            dataBuilder.putStringArray(REQUEST_TARGETS, targets.map { it.name }.toTypedArray())
            builder.setInputData(dataBuilder.build())

            val workRequest = builder.build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }

    override fun doWork(): Result {
        if (TextSecurePreferences.getLocalNumber(context) == null) {
            Log.v(TAG, "User not registered yet.")
            return Result.failure()
        }

        // If this is a scheduled run and it is happening before the initial scheduled time (as
        // periodic background tasks run immediately when scheduled) then don't actually do anything
        // because this might slow requests on initial startup or triggered by PNs
        val initialScheduleTime = inputData.getLong(INITIAL_SCHEDULE_TIME, -1)

        if (initialScheduleTime != -1L && System.currentTimeMillis() < (initialScheduleTime - (60 * 1000))) {
            Log.v(TAG, "Skipping initial run.")
            return Result.success()
        }

        // Retrieve the desired targets (defaulting to all if not provided or empty)
        val requestTargets: List<Targets> = (inputData.getStringArray(REQUEST_TARGETS) ?: emptyArray())
            .map {
                try { Targets.valueOf(it) }
                catch(e: Exception) { null }
            }
            .filterNotNull()
            .ifEmpty { Targets.values().toList() }

        try {
            Log.v(TAG, "Performing background poll for ${requestTargets.joinToString { it.name }}.")
            val promises = mutableListOf<Promise<Unit, Exception>>()

            // DMs
            var dmsPromise: Promise<Unit, Exception> = Promise.ofSuccess(Unit)

            if (requestTargets.contains(Targets.DMS)) {
                val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
                dmsPromise = SnodeAPI.getMessages(userPublicKey).bind { envelopes ->
                    val params = envelopes.map { (envelope, serverHash) ->
                        // FIXME: Using a job here seems like a bad idea...
                        MessageReceiveParameters(envelope.toByteArray(), serverHash, null)
                    }
                    BatchMessageReceiveJob(params).executeAsync("background")
                }
                promises.add(dmsPromise)
            }

            // Closed groups
            if (requestTargets.contains(Targets.CLOSED_GROUPS)) {
                val closedGroupPoller = ClosedGroupPollerV2() // Intentionally don't use shared
                val storage = MessagingModuleConfiguration.shared.storage
                val allGroupPublicKeys = storage.getAllClosedGroupPublicKeys()
                allGroupPublicKeys.iterator().forEach { closedGroupPoller.poll(it) }
            }

            // Open Groups
            var ogPollError: Exception? = null

            if (requestTargets.contains(Targets.OPEN_GROUPS)) {
                val threadDB = DatabaseComponent.get(context).lokiThreadDatabase()
                val openGroups = threadDB.getAllOpenGroups()
                val openGroupServers = openGroups.map { it.value.server }.toSet()

                for (server in openGroupServers) {
                    val poller = OpenGroupPoller(server, null)
                    poller.hasStarted = true

                    // If one of the open group pollers fails we don't want it to cancel the DM
                    // poller so just hold on to the error for later
                    promises.add(
                        poller.poll().recover {
                            if (dmsPromise.isDone()) {
                                throw it
                            }

                            ogPollError = it
                        }
                    )
                }
            }

            // Wait until all the promises are resolved
            all(promises).get()

            // If the Open Group pollers threw an exception then re-throw it here (now that
            // the DM promise has completed)
            val localOgPollException = ogPollError

            if (localOgPollException != null) {
                throw localOgPollException
            }

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
