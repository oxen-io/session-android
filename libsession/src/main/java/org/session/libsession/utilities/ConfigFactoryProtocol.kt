package org.session.libsession.utilities

import network.loki.messenger.libsession_util.Config
import network.loki.messenger.libsession_util.ConfigBase
import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.ConversationVolatileConfig
import network.loki.messenger.libsession_util.GroupInfoConfig
import network.loki.messenger.libsession_util.GroupKeysConfig
import network.loki.messenger.libsession_util.GroupMembersConfig
import network.loki.messenger.libsession_util.UserGroupsConfig
import network.loki.messenger.libsession_util.UserProfile
import org.session.libsession.messaging.messages.Destination
import org.session.libsignal.utilities.AccountId

interface ConfigFactoryProtocol {

    val user: UserProfile?
    val contacts: Contacts?
    val convoVolatile: ConversationVolatileConfig?
    val userGroups: UserGroupsConfig?

    fun getGroupInfoConfig(groupSessionId: AccountId): GroupInfoConfig?
    fun getGroupMemberConfig(groupSessionId: AccountId): GroupMembersConfig?
    fun getGroupKeysConfig(groupSessionId: AccountId,
                           info: GroupInfoConfig? = null,
                           members: GroupMembersConfig? = null,
                           free: Boolean = true): GroupKeysConfig?

    fun getUserConfigs(): List<ConfigBase>
    fun persist(forConfigObject: Config, timestamp: Long, forPublicKey: String? = null)

    fun conversationInConfig(publicKey: String?, groupPublicKey: String?, openGroupId: String?, visibleOnly: Boolean): Boolean
    fun canPerformChange(variant: String, publicKey: String, changeTimestampMs: Long): Boolean
    fun saveGroupConfigs(
        groupKeys: GroupKeysConfig,
        groupInfo: GroupInfoConfig,
        groupMembers: GroupMembersConfig
    )
    fun removeGroup(closedGroupId: AccountId)

    fun scheduleUpdate(destination: Destination)
    fun constructGroupKeysConfig(
        groupSessionId: AccountId,
        info: GroupInfoConfig,
        members: GroupMembersConfig
    ): GroupKeysConfig?

    fun maybeDecryptForUser(encoded: ByteArray,
                            domain: String,
                            closedGroupSessionId: AccountId): ByteArray?

    fun userSessionId(): AccountId?

}

interface ConfigFactoryUpdateListener {
    fun notifyUpdates(forConfigObject: Config, messageTimestamp: Long)
}