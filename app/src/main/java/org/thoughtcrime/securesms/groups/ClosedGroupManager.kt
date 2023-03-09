package org.thoughtcrime.securesms.groups

import android.content.Context
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationAPI
import org.session.libsession.messaging.sending_receiving.pollers.ClosedGroupPollerV2
import org.session.libsession.utilities.Address
import org.thoughtcrime.securesms.ApplicationContext

object ClosedGroupManager {

    fun silentlyRemoveGroup(context: Context, threadId: Long, groupPublicKey: String, groupID: String, userPublicKey: String, delete: Boolean = true) {
        val storage = MessagingModuleConfiguration.shared.storage
        storage.removeClosedGroupPublicKey(groupPublicKey)
        // Remove the key pairs
        storage.removeAllClosedGroupEncryptionKeyPairs(groupPublicKey)
        // Mark the group as inactive
        storage.setActive(groupID, false)
        storage.removeMember(groupID, Address.fromSerialized(userPublicKey))
        // Notify the PN server
        PushNotificationAPI.performOperation(PushNotificationAPI.ClosedGroupOperation.Unsubscribe, groupPublicKey, userPublicKey)
        // Stop polling
        ClosedGroupPollerV2.shared.stopPolling(groupPublicKey)
        storage.cancelPendingMessageSendJobs(threadId)
        ApplicationContext.getInstance(context).messageNotifier.updateNotification(context)
        if (delete) {
            storage.deleteConversation(threadId)
        }
    }

}