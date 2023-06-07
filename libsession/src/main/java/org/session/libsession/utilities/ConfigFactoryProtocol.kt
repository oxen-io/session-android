package org.session.libsession.utilities

import network.loki.messenger.libsession_util.ConfigBase
import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.ConversationVolatileConfig
import network.loki.messenger.libsession_util.UserGroupsConfig
import network.loki.messenger.libsession_util.UserProfile

interface ConfigFactoryProtocol {
    val user: UserProfile?
    val contacts: Contacts?
    val convoVolatile: ConversationVolatileConfig?
    val userGroups: UserGroupsConfig?
    fun getUserConfigs(): List<ConfigBase>
    fun persist(forConfigObject: ConfigBase, timestamp: Long)

    fun conversationInConfig(publicKey: String?, groupPublicKey: String?, openGroupId: String?, visibleOnly: Boolean): Boolean
    fun canPerformChange(variant: String, publicKey: String, changeTimestampMs: Long): Boolean
}

interface ConfigFactoryUpdateListener {
    fun notifyUpdates(forConfigObject: ConfigBase)
}