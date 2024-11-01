package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.control.ClosedGroupControlMessage
import org.session.libsession.messaging.sending_receiving.MessageReceiver
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.sending_receiving.disableLocalGroupAndUnsubscribe
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.messages.SignalServiceGroup
import org.session.libsignal.utilities.Log

class GroupLeavingJob(val groupPublicKey: String, val notifyUser: Boolean, val deleteThread: Boolean): Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0

    override val maxFailureCount: Int = 0

    companion object {
        val TAG = GroupLeavingJob::class.simpleName
        val KEY: String = "GroupLeavingJob"

        // Keys used for database storage
        private val GROUP_PUBLIC_KEY_KEY = "group_public_key"
        private val NOTIFY_USER_KEY = "notify_user"
        private val DELETE_THREAD_KEY = "delete_thread"
    }

    override suspend fun execute(dispatcherName: String) {
        val context = MessagingModuleConfiguration.shared.context
        val storage = MessagingModuleConfiguration.shared.storage
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
        val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
        val group = storage.getGroup(groupID) ?: return handlePermanentFailure(dispatcherName, MessageSender.Error.NoThread)
        val updatedMembers = group.members.map { it.serialize() }.toSet() - userPublicKey
        val admins = group.admins.map { it.serialize() }
        val name = group.title
        // Send the update to the group
        val closedGroupControlMessage = ClosedGroupControlMessage(ClosedGroupControlMessage.Kind.MemberLeft())
        val sentTime = SnodeAPI.nowWithOffset
        closedGroupControlMessage.sentTimestamp = sentTime
        storage.setActive(groupID, false)
        var messageId: Long? = null
        val threadID = storage.getOrCreateThreadIdFor(Address.fromSerialized(groupID))
        if (notifyUser) {
            val infoType = SignalServiceGroup.Type.LEAVING
            messageId = storage.insertOutgoingInfoMessage(context, groupID, infoType, name, updatedMembers, admins, threadID, sentTime)
        }
        MessageSender.sendNonDurably(closedGroupControlMessage, Address.fromSerialized(groupID), false).success {
            // Notify the user
            if (notifyUser && (messageId != null)) {
                val infoType = SignalServiceGroup.Type.QUIT
                storage.updateInfoMessage(context, messageId, groupID, infoType, name, updatedMembers)
            }
            // Remove the group private key and unsubscribe from PNs
            MessageReceiver.disableLocalGroupAndUnsubscribe(groupPublicKey, groupID, userPublicKey, deleteThread)
            handleSuccess(dispatcherName)
        }.fail {
            storage.setActive(groupID, true)
            if (notifyUser && (messageId != null)) {
                val infoType = SignalServiceGroup.Type.ERROR_QUIT
                storage.updateInfoMessage(context, messageId, groupID, infoType, name, updatedMembers)
            }
            handleFailure(dispatcherName, it)
        }
    }

    private fun handleSuccess(dispatcherName: String) {
        Log.w(TAG, "Group left successfully.")
        delegate?.handleJobSucceeded(this, dispatcherName)
    }

    private fun handlePermanentFailure(dispatcherName: String, e: Exception) {
        delegate?.handleJobFailedPermanently(this, dispatcherName, e)
    }

    private fun handleFailure(dispatcherName: String, e: Exception) {
        delegate?.handleJobFailed(this, dispatcherName, e)
    }

    override fun serialize(): Data {
        return Data.Builder()
                .putString(GROUP_PUBLIC_KEY_KEY, groupPublicKey)
                .putBoolean(NOTIFY_USER_KEY, notifyUser)
                .putBoolean(DELETE_THREAD_KEY, deleteThread)
                .build()
    }

    override fun getFactoryKey(): String {
        return KEY
    }

    class Factory : Job.Factory<GroupLeavingJob> {

        override fun create(data: Data): GroupLeavingJob {
            return GroupLeavingJob(
                    data.getString(GROUP_PUBLIC_KEY_KEY),
                    data.getBoolean(NOTIFY_USER_KEY),
                    data.getBoolean(DELETE_THREAD_KEY)
            )
        }
    }
}