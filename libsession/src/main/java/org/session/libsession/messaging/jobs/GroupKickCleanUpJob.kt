package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.utilities.Data
import org.session.libsignal.utilities.SessionId


class GroupKickCleanUpJob(private val groupId: SessionId, private val removeMessages: Boolean) :
    Job {
    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int get() = 1

    override suspend fun execute(dispatcherName: String) {
        TODO("Not yet implemented")
    }

    override fun serialize(): Data = Data.Builder()
        .putString(DATA_KEY_GROUP_ID, groupId.hexString())
        .putBoolean(DATA_KEY_REMOVE_MESSAGES, removeMessages)
        .build()

    override fun getFactoryKey() = KEY

    companion object {
        const val KEY = "GroupKickCleanUpJob"

        private const val DATA_KEY_GROUP_ID = "groupId"
        private const val DATA_KEY_REMOVE_MESSAGES = "removeMessages"
    }

    class Factory : Job.Factory<GroupKickCleanUpJob> {
        override fun create(data: Data): GroupKickCleanUpJob? {
            return GroupKickCleanUpJob(
                groupId = SessionId.from(data.getString(DATA_KEY_GROUP_ID) ?: return null),
                removeMessages = data.getBooleanOrDefault(DATA_KEY_REMOVE_MESSAGES, false)
            )
        }
    }
}
