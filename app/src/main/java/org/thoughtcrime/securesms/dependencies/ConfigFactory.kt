package org.thoughtcrime.securesms.dependencies

import android.content.Context
import network.loki.messenger.libsession_util.ConfigBase
import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.ConversationVolatileConfig
import network.loki.messenger.libsession_util.UserProfile
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.ConfigFactoryUpdateListener
import org.session.libsignal.protos.SignalServiceProtos.SharedConfigMessage
import org.thoughtcrime.securesms.database.ConfigDatabase
import java.util.concurrent.ConcurrentSkipListSet

class ConfigFactory(private val context: Context,
                    private val configDatabase: ConfigDatabase,
                    private val maybeGetUserInfo: ()->Pair<ByteArray, String>?):
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
    private val userHashes = ConcurrentSkipListSet<String>()
    private val contactsLock = Object()
    private var _contacts: Contacts? = null
    private val contactsHashes = ConcurrentSkipListSet<String>()
    private val convoVolatileLock = Object()
    private var _convoVolatileConfig: ConversationVolatileConfig? = null
    private val convoHashes = ConcurrentSkipListSet<String>()

    private val listeners: MutableList<ConfigFactoryUpdateListener> = mutableListOf()
    fun registerListener(listener: ConfigFactoryUpdateListener) { listeners += listener }
    fun unregisterListener(listener: ConfigFactoryUpdateListener) { listeners -= listener }

    override val user: UserProfile? = synchronized(userLock) {
        if (_userConfig == null) {
            val (secretKey, publicKey) = maybeGetUserInfo() ?: return@synchronized null
            val userDump = configDatabase.retrieveConfigAndHashes(SharedConfigMessage.Kind.USER_PROFILE.name, publicKey)
            _userConfig = if (userDump != null) {
                val (bytes, hashes) = userDump
                userHashes.clear()
                userHashes.addAll(hashes)
                UserProfile.newInstance(secretKey, bytes)
            } else {
                UserProfile.newInstance(secretKey)
            }
        }
        _userConfig
    }

    override val contacts: Contacts? = synchronized(contactsLock) {
        if (_contacts == null) {
            val (secretKey, publicKey) = maybeGetUserInfo() ?: return@synchronized null
            val contactsDump = configDatabase.retrieveConfigAndHashes(SharedConfigMessage.Kind.CONTACTS.name, publicKey)
            _contacts = if (contactsDump != null) {
                val (bytes, hashes) = contactsDump
                contactsHashes.clear()
                contactsHashes.addAll(hashes)
                Contacts.newInstance(secretKey, bytes)
            } else {
                Contacts.newInstance(secretKey)
            }
        }
        _contacts
    }

    override val convoVolatile: ConversationVolatileConfig? = synchronized(convoVolatileLock) {
        if (_convoVolatileConfig == null) {
            val (secretKey, publicKey) = maybeGetUserInfo() ?: return@synchronized null
            val convoDump = configDatabase.retrieveConfigAndHashes(SharedConfigMessage.Kind.CONVO_INFO_VOLATILE.name, publicKey)
            _convoVolatileConfig = if (convoDump != null) {
                val (bytes, hashes) = convoDump
                convoHashes.clear()
                convoHashes.addAll(hashes)
                ConversationVolatileConfig.newInstance(secretKey, bytes)
            } else {
                ConversationVolatileConfig.newInstance(secretKey)
            }
        }
        _convoVolatileConfig
    }


    private fun persistUserConfigDump() = synchronized(userLock) {
        val dumped = user?.dump() ?: return
        val (_, publicKey) = maybeGetUserInfo() ?: return
        configDatabase.storeConfig(SharedConfigMessage.Kind.USER_PROFILE.name, publicKey, dumped, userHashes.toList())
    }

    private fun persistContactsConfigDump() = synchronized(contactsLock) {
        val dumped = contacts?.dump() ?: return
        val (_, publicKey) = maybeGetUserInfo() ?: return
        configDatabase.storeConfig(SharedConfigMessage.Kind.CONTACTS.name, publicKey, dumped, contactsHashes.toList())
    }

    private fun persistConvoVolatileConfigDump() = synchronized (convoVolatileLock) {
        val dumped = convoVolatile?.dump() ?: return
        val (_, publicKey) = maybeGetUserInfo() ?: return
        configDatabase.storeConfig(SharedConfigMessage.Kind.CONVO_INFO_VOLATILE.name, publicKey, dumped, convoHashes.toList())
    }

    override fun persist(forConfigObject: ConfigBase) {
        listeners.forEach { listener ->
            listener.notifyUpdates(forConfigObject)
        }
        when (forConfigObject) {
            is UserProfile -> persistUserConfigDump()
            is Contacts -> persistContactsConfigDump()
            is ConversationVolatileConfig -> persistConvoVolatileConfigDump()
        }
    }

    override fun appendHash(configObject: ConfigBase, hash: String) {
        when (configObject) {
            is UserProfile -> userHashes.add(hash)
            is Contacts -> contactsHashes.add(hash)
            is ConversationVolatileConfig -> convoHashes.add(hash)
        }
    }

    override fun getHashesFor(forConfigObject: ConfigBase): List<String> =
        when (forConfigObject) {
            is UserProfile -> userHashes.toList()
            is Contacts -> contactsHashes.toList()
            is ConversationVolatileConfig -> convoHashes.toList()
        }

    override fun removeHashesFor(forConfigObject: ConfigBase, deletedHashes: Set<String>) =
        when (forConfigObject) {
            is UserProfile -> userHashes.removeAll(deletedHashes)
            is Contacts -> contactsHashes.removeAll(deletedHashes)
            is ConversationVolatileConfig -> convoHashes.removeAll(deletedHashes)
        }

}