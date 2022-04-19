package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.utilities.Data

class OpenGroupDeleteJob(val messageIds: LongArray): Job {

    companion object {
        const val KEY = "OpenGroupDeleteJob"
        private const val MESSAGE_IDS = "messageIds"
    }

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 1

    override fun execute() {
        TODO("Not yet implemented")
    }

    override fun serialize(): Data = Data.Builder()
            .putLongArray(MESSAGE_IDS, messageIds)
            .build()

    override fun getFactoryKey(): String = KEY

    class Factory: Job.Factory<OpenGroupDeleteJob> {
        override fun create(data: Data): OpenGroupDeleteJob {
            val messageIds = data.getLongArray(MESSAGE_IDS)
            return OpenGroupDeleteJob(messageIds)
        }
    }

}