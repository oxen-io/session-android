package org.session.libsession.utilities

import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.UserProfile

interface ConfigFactoryProtocol {
    val userConfig: UserProfile?
    val contacts: Contacts?
    fun saveUserConfigDump()
    fun saveContactConfigDump()
}