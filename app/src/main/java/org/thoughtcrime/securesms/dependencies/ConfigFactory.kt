package org.thoughtcrime.securesms.dependencies

import android.content.Context
import network.loki.messenger.libsession_util.ConfigBase
import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.ConversationVolatileConfig
import network.loki.messenger.libsession_util.UserGroupsConfig
import network.loki.messenger.libsession_util.UserProfile
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.ConfigFactoryUpdateListener
import org.session.libsignal.protos.SignalServiceProtos.SharedConfigMessage
import org.thoughtcrime.securesms.database.ConfigDatabase
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities

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
    private val contactsLock = Object()
    private var _contacts: Contacts? = null
    private val convoVolatileLock = Object()
    private var _convoVolatileConfig: ConversationVolatileConfig? = null
    private val userGroupsLock = Object()
    private var _userGroups: UserGroupsConfig? = null

    private val listeners: MutableList<ConfigFactoryUpdateListener> = mutableListOf()
    fun registerListener(listener: ConfigFactoryUpdateListener) { listeners += listener }
    fun unregisterListener(listener: ConfigFactoryUpdateListener) { listeners -= listener }

    override val user: UserProfile? get() = synchronized(userLock) {
        if (_userConfig == null) {
            val (secretKey, publicKey) = maybeGetUserInfo() ?: return@synchronized null
            val userDump = configDatabase.retrieveConfigAndHashes(SharedConfigMessage.Kind.USER_PROFILE.name, publicKey)
            _userConfig = if (userDump != null) {
                UserProfile.newInstance(secretKey, userDump)
            } else {
                ConfigurationMessageUtilities.generateUserProfileConfigDump()?.let { dump ->
                    UserProfile.newInstance(secretKey, dump)
                } ?: UserProfile.newInstance(secretKey)
            }
        }
        _userConfig
    }

    override val contacts: Contacts? get() = synchronized(contactsLock) {
        if (_contacts == null) {
            val (secretKey, publicKey) = maybeGetUserInfo() ?: return@synchronized null
            val contactsDump = configDatabase.retrieveConfigAndHashes(SharedConfigMessage.Kind.CONTACTS.name, publicKey)
            _contacts = if (contactsDump != null) {
                Contacts.newInstance(secretKey, contactsDump)
            } else {
                ConfigurationMessageUtilities.generateContactConfigDump()?.let { dump ->
                    Contacts.newInstance(secretKey, dump)
                } ?: Contacts.newInstance(secretKey)
            }
        }
        _contacts
    }

    override val convoVolatile: ConversationVolatileConfig? get() = synchronized(convoVolatileLock) {
        if (_convoVolatileConfig == null) {
            val (secretKey, publicKey) = maybeGetUserInfo() ?: return@synchronized null
            val convoDump = configDatabase.retrieveConfigAndHashes(SharedConfigMessage.Kind.CONVO_INFO_VOLATILE.name, publicKey)
            _convoVolatileConfig = if (convoDump != null) {
                ConversationVolatileConfig.newInstance(secretKey, convoDump)
            } else {
                ConfigurationMessageUtilities.generateConversationVolatileDump(context)?.let { dump ->
                    ConversationVolatileConfig.newInstance(secretKey, dump)
                } ?: ConversationVolatileConfig.newInstance(secretKey)
            }
        }
        _convoVolatileConfig
    }

    override val userGroups: UserGroupsConfig? get() = synchronized(userGroupsLock) {
        if (_userGroups == null) {
            val (secretKey, publicKey) = maybeGetUserInfo() ?: return@synchronized null
            val userGroupsDump = configDatabase.retrieveConfigAndHashes(SharedConfigMessage.Kind.GROUPS.name, publicKey)
            _userGroups = if (userGroupsDump != null) {
                UserGroupsConfig.Companion.newInstance(secretKey, userGroupsDump)
            } else {
                ConfigurationMessageUtilities.generateUserGroupDump(context)?.let { dump ->
                    UserGroupsConfig.Companion.newInstance(secretKey, dump)
                } ?: UserGroupsConfig.newInstance(secretKey)
            }
        }
        _userGroups
    }

    override fun getUserConfigs(): List<ConfigBase> = listOfNotNull(user, contacts, convoVolatile, userGroups)


    private fun persistUserConfigDump() = synchronized(userLock) {
        val dumped = user?.dump() ?: return
        val (_, publicKey) = maybeGetUserInfo() ?: return
        configDatabase.storeConfig(SharedConfigMessage.Kind.USER_PROFILE.name, publicKey, dumped)
    }

    private fun persistContactsConfigDump() = synchronized(contactsLock) {
        val dumped = contacts?.dump() ?: return
        val (_, publicKey) = maybeGetUserInfo() ?: return
        configDatabase.storeConfig(SharedConfigMessage.Kind.CONTACTS.name, publicKey, dumped)
    }

    private fun persistConvoVolatileConfigDump() = synchronized (convoVolatileLock) {
        val dumped = convoVolatile?.dump() ?: return
        val (_, publicKey) = maybeGetUserInfo() ?: return
        configDatabase.storeConfig(SharedConfigMessage.Kind.CONVO_INFO_VOLATILE.name, publicKey, dumped)
    }

    private fun persistUserGroupsConfigDump() = synchronized(userGroupsLock) {
        val dumped = userGroups?.dump() ?: return
        val (_, publicKey) = maybeGetUserInfo() ?: return
        configDatabase.storeConfig(SharedConfigMessage.Kind.GROUPS.name, publicKey, dumped)
    }

    override fun persist(forConfigObject: ConfigBase) {
        listeners.forEach { listener ->
            listener.notifyUpdates(forConfigObject)
        }
        when (forConfigObject) {
            is UserProfile -> persistUserConfigDump()
            is Contacts -> persistContactsConfigDump()
            is ConversationVolatileConfig -> persistConvoVolatileConfigDump()
            is UserGroupsConfig -> persistUserGroupsConfigDump()
            else -> throw UnsupportedOperationException("Can't support type of ${forConfigObject::class.simpleName} yet")
        }
    }

}