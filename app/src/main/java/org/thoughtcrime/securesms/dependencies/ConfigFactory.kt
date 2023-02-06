package org.thoughtcrime.securesms.dependencies

import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.ConversationVolatileConfig
import network.loki.messenger.libsession_util.UserProfile
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsignal.protos.SignalServiceProtos.SharedConfigMessage
import org.thoughtcrime.securesms.database.ConfigDatabase

class ConfigFactory(private val configDatabase: ConfigDatabase, private val maybeGetUserInfo: ()->Pair<ByteArray, String>?):
    ConfigFactoryProtocol {

    fun keyPairChanged() { // this should only happen restoring or clearing data
        _userConfig?.free()
        _contacts?.free()
        _convoVolatileConfig?.free()
        _userConfig = null
        _contacts = null
        _convoVolatileConfig = null
    }

    private val userLock = Object()
    private var _userConfig: UserProfile? = null
    private val contactLock = Object()
    private var _contacts: Contacts? = null
    private val convoVolatileLock = Object()
    private var _convoVolatileConfig: ConversationVolatileConfig? = null

    override val user: UserProfile? = synchronized(userLock) {
        if (_userConfig == null) {
            val (secretKey, publicKey) = maybeGetUserInfo() ?: return@synchronized null
            val userDump = configDatabase.retrieveConfig(SharedConfigMessage.Kind.USER_PROFILE.name, publicKey)
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
            val (secretKey, publicKey) = maybeGetUserInfo() ?: return@synchronized null
            val contactsDump = configDatabase.retrieveConfig(SharedConfigMessage.Kind.CONTACTS.name, publicKey)
            _contacts = if (contactsDump != null) {
                Contacts.newInstance(secretKey, contactsDump)
            } else {
                Contacts.newInstance(secretKey)
            }
        }
        _contacts
    }

    override val convoVolatile: ConversationVolatileConfig? = synchronized(convoVolatileLock) {
        if (_convoVolatileConfig == null) {
            val (secretKey, publicKey) = maybeGetUserInfo() ?: return@synchronized null
            val convoDump = configDatabase.retrieveConfig(SharedConfigMessage.Kind.CONVO_INFO_VOLATILE.name, publicKey)
            _convoVolatileConfig = if (convoDump != null) {
                ConversationVolatileConfig.newInstance(secretKey, convoDump)
            } else {
                ConversationVolatileConfig.newInstance(secretKey)
            }
        }
        _convoVolatileConfig
    }


    override fun saveUserConfigDump() {
        val dumped = user?.dump() ?: return
        val (_, publicKey) = maybeGetUserInfo() ?: return
        configDatabase.storeConfig(SharedConfigMessage.Kind.USER_PROFILE.name, publicKey, dumped)
    }

    override fun saveContactConfigDump() {
        val dumped = contacts?.dump() ?: return
        val (_, publicKey) = maybeGetUserInfo() ?: return
        configDatabase.storeConfig(SharedConfigMessage.Kind.CONTACTS.name, publicKey, dumped)
    }

    override fun saveConvoVolatileConfigDump() {
        val dumped = convoVolatile?.dump() ?: return
        val (_, publicKey) = maybeGetUserInfo() ?: return
        configDatabase.storeConfig(SharedConfigMessage.Kind.CONVO_INFO_VOLATILE.name, publicKey, dumped)
    }
}