package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.utilities.Data
import org.session.libsignal.utilities.Log

class OpenGroupDeleteJob(private val messageServerIds: LongArray, private val threadId: Long, val openGroupId: String): Job {

    companion object {
        private const val TAG = "OpenGroupDeleteJob"
        const val KEY = "OpenGroupDeleteJob"
        private const val MESSAGE_IDS = "messageIds"
        private const val THREAD_ID = "threadId"
        private const val OPEN_GROUP_ID = "openGroupId"
    }

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 1

    override suspend fun execute(dispatcherName: String) {
        val dataProvider = MessagingModuleConfiguration.shared.messageDataProvider
        val numberToDelete = messageServerIds.size
        Log.d(TAG, "Deleting $numberToDelete messages")

        // FIXME: This entire process should probably run in a transaction (with the attachment deletion happening only if it succeeded)
        try {
            val messageIds = dataProvider.getMessageIDs(messageServerIds.toList(), threadId)

            // Delete the SMS messages
            if (messageIds.first.isNotEmpty()) {
                dataProvider.deleteMessages(messageIds.first, threadId, true)
            }

            // Delete the MMS messages
            if (messageIds.second.isNotEmpty()) {
                dataProvider.deleteMessages(messageIds.second, threadId, false)
            }

            Log.d(TAG, "Deleted ${messageIds.first.size + messageIds.second.size} messages successfully")
            delegate?.handleJobSucceeded(this, dispatcherName)
        }
        catch (e: Exception) {
            delegate?.handleJobFailed(this, dispatcherName, e)
        }
    }

    override fun serialize(): Data = Data.Builder()
        .putLongArray(MESSAGE_IDS, messageServerIds)
        .putLong(THREAD_ID, threadId)
        .putString(OPEN_GROUP_ID, openGroupId)
        .build()

    override fun getFactoryKey(): String = KEY

    class Factory: Job.Factory<OpenGroupDeleteJob> {
        override fun create(data: Data): OpenGroupDeleteJob {
            val messageServerIds = data.getLongArray(MESSAGE_IDS)
            val threadId = data.getLong(THREAD_ID)
            val openGroupId = data.getString(OPEN_GROUP_ID)
            return OpenGroupDeleteJob(messageServerIds, threadId, openGroupId)
        }
    }

}