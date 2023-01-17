package org.thoughtcrime.securesms.dependencies

import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.UserProfile
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.thoughtcrime.securesms.database.ConfigDatabase

class ConfigFactory(private val configDatabase: ConfigDatabase, private val maybeGetUserEdSecretKey: ()->ByteArray?):
    ConfigFactoryProtocol {

    fun keyPairChanged() { // this should only happen restoring or clearing data
        _userConfig?.free()
        _contacts?.free()
        _userConfig = null
        _contacts = null
    }

    private val userLock = Object()
    private var _userConfig: UserProfile? = null
    private val contactLock = Object()
    private var _contacts: Contacts? = null

    override val userConfig: UserProfile? = synchronized(userLock) {
        if (_userConfig == null) {
            val secretKey = maybeGetUserEdSecretKey() ?: return@synchronized null
            val userDump = configDatabase.retrieveConfig(ConfigDatabase.USER_KEY)
            _userConfig = if (userDump != null) {
                UserProfile.newInstance(secretKey, userDump)
            } else {
                UserProfile.newInstance(secretKey)
            }
        }
        _userConfig
    }

    override val contacts: Contacts? = synchronized(contactLock) {
        if (_contacts == null) {
            val secretKey = maybeGetUserEdSecretKey() ?: return@synchronized null
            val contactsDump = configDatabase.retrieveConfig(ConfigDatabase.CONTACTS_KEY)
            _contacts = if (contactsDump != null) {
                Contacts.newInstance(secretKey, contactsDump)
            } else {
                Contacts.newInstance(secretKey)
            }
        }
        _contacts
    }


    override fun saveUserConfigDump() {
        val dumped = userConfig?.dump() ?: return
        configDatabase.storeConfig(ConfigDatabase.USER_KEY, dumped)
    }

    override fun saveContactConfigDump() {
        val dumped = contacts?.dump() ?: return
        configDatabase.storeConfig(ConfigDatabase.CONTACTS_KEY, dumped)
    }

}