package org.session.libsession.utilities

import network.loki.messenger.libsession_util.ConfigBase
import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.ConversationVolatileConfig
import network.loki.messenger.libsession_util.UserProfile

interface ConfigFactoryProtocol {
    val user: UserProfile?
    val contacts: Contacts?
    val convoVolatile: ConversationVolatileConfig?
    fun persist(forConfigObject: ConfigBase)
    fun appendHash(configObject: ConfigBase, hash: String)
    fun getHashesFor(forConfigObject: ConfigBase): List<String>
    fun removeHashesFor(config: ConfigBase, deletedHashes: Set<String>): Boolean
}

interface ConfigFactoryUpdateListener {
    fun notifyUpdates(forConfigObject: ConfigBase)
}