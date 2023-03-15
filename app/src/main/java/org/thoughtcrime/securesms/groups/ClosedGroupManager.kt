package org.thoughtcrime.securesms.groups

import android.content.Context
import network.loki.messenger.libsession_util.util.GroupInfo
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationAPI
import org.session.libsession.messaging.sending_receiving.pollers.ClosedGroupPollerV2
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupRecord
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.dependencies.ConfigFactory

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

    fun ConfigFactory.removeLegacyGroup(group: GroupRecord): Boolean {
        val groups = userGroups ?: return false
        if (!group.isClosedGroup) return false
        val groupPublicKey = GroupUtil.doubleEncodeGroupID(group.getId())
        return groups.eraseLegacyGroup(groupPublicKey)
    }

    fun ConfigFactory.updateLegacyGroup(groupRecipientSettings: Recipient.RecipientSettings, group: GroupRecord) {
        val groups = userGroups ?: return
        if (!group.isClosedGroup) return
        val storage = MessagingModuleConfiguration.shared.storage
        val threadId = storage.getThreadId(group.encodedId) ?: return
        val groupPublicKey = GroupUtil.doubleEncodeGroupID(group.getId())
        val latestKeyPair = storage.getLatestClosedGroupEncryptionKeyPair(groupPublicKey) ?: return
        val legacyInfo = groups.getOrConstructLegacyGroupInfo(groupPublicKey)
        val latestMemberMap = GroupUtil.createConfigMemberMap(group.members.map(Address::serialize), group.admins.map(Address::serialize))
        val toSet = legacyInfo.copy(
            members = latestMemberMap,
            name = group.title,
            disappearingTimer = groupRecipientSettings.expireMessages.toLong(),
            priority = if (storage.isPinned(threadId)) 1 else 0,
            encSecKey = latestKeyPair.privateKey.serialize(),
            encPubKey = latestKeyPair.publicKey.serialize()
        )
        groups.set(toSet)
    }

}