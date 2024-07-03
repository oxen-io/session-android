package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.snode.SnodeAPI
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.SessionId


/**
 * A job that cleans up state and data after being kicked from a group.
 */
class GroupKickCleanUpJob(private val groupId: SessionId, private val removeMessages: Boolean) :
    Job {
    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int get() = 1

    override suspend fun execute(dispatcherName: String) {
        try {
            doExecute()
            delegate?.handleJobSucceeded(this, dispatcherName)
        } catch (e: Exception) {
            delegate?.handleJobFailed(this, dispatcherName, e)
        }
    }

    private fun doExecute() {
        val configFactory = MessagingModuleConfiguration.shared.configFactory
        val userGroups = configFactory.userGroups
            ?: return Log.d(LOG_TAG, "UserGroups config doesn't exist")

        val group = userGroups.getClosedGroup(groupId.hexString())?.setKicked()
            ?: return Log.d(LOG_TAG, "Group doesn't exist")

        userGroups.set(group)
        configFactory.persist(userGroups, SnodeAPI.nowWithOffset)
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

        private const val LOG_TAG = "GroupKickCleanUpJob"
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
