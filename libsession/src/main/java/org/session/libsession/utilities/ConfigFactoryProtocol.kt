package org.session.libsession.utilities

import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.ConversationVolatileConfig
import network.loki.messenger.libsession_util.UserProfile

interface ConfigFactoryProtocol {
    val user: UserProfile?
    val contacts: Contacts?
    val convoVolatile: ConversationVolatileConfig?
    fun saveUserConfigDump()
    fun saveContactConfigDump()
    fun saveConvoVolatileConfigDump()
}