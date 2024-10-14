package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.messaging.utilities.UpdateMessageData
import org.session.libsignal.utilities.AccountId

class LibSessionGroupLeavingJob(val accountId: AccountId, val deleteOnLeave: Boolean): Job {


    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 4

    override suspend fun execute(dispatcherName: String) {
        val storage = MessagingModuleConfiguration.shared.storage
        // start leaving
        // create message ID with leaving state
        val messageId = storage.insertGroupInfoLeaving(accountId) ?: run {
            delegate?.handleJobFailedPermanently(
                this,
                dispatcherName,
                Exception("Couldn't insert GroupInfoLeaving message in leaving group job")
            )
            return
        }
        // do actual group leave request

        // on success
        val leaveGroup = kotlin.runCatching {
            MessagingModuleConfiguration.shared.groupManagerV2.leaveGroup(accountId, deleteOnLeave)
        }

        if (leaveGroup.isSuccess) {
            // message is already deleted, succeed
            delegate?.handleJobSucceeded(this, dispatcherName)
        } else {
            // Error leaving group, update the info message
            storage.updateGroupInfoChange(messageId, UpdateMessageData.Kind.GroupErrorQuit)
        }
    }

    override fun serialize(): Data =
        Data.Builder()
            .putString(SESSION_ID_KEY, accountId.hexString)
            .putBoolean(DELETE_ON_LEAVE_KEY, deleteOnLeave)
            .build()

    class Factory : Job.Factory<LibSessionGroupLeavingJob> {
        override fun create(data: Data): LibSessionGroupLeavingJob {
            return LibSessionGroupLeavingJob(
                AccountId(data.getString(SESSION_ID_KEY)),
                data.getBoolean(DELETE_ON_LEAVE_KEY)
            )
        }
    }

    override fun getFactoryKey(): String = KEY

    companion object {
        const val KEY = "LibSessionGroupLeavingJob"
        private const val SESSION_ID_KEY = "SessionId"
        private const val DELETE_ON_LEAVE_KEY = "DeleteOnLeave"
    }

}