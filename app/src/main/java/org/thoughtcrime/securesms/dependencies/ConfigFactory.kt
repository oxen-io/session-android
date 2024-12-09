package org.thoughtcrime.securesms.dependencies

import android.content.Context
import android.os.Trace
import network.loki.messenger.libsession_util.ConfigBase
import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.ConversationVolatileConfig
import network.loki.messenger.libsession_util.UserGroupsConfig
import network.loki.messenger.libsession_util.UserProfile
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.ConfigFactoryUpdateListener
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.protos.SignalServiceProtos.SharedConfigMessage
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.database.ConfigDatabase
import org.thoughtcrime.securesms.dependencies.DatabaseComponent.Companion.get
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities

class ConfigFactory(
    private val context: Context,
    private val configDatabase: ConfigDatabase,
    private val maybeGetUserInfo: () -> Pair<ByteArray, String>?
) :
    ConfigFactoryProtocol {
    companion object {
        // This is a buffer period within which we will process messages which would result in a
        // config change, any message which would normally result in a config change which was sent
        // before `lastConfigMessage.timestamp - configChangeBufferPeriod` will not  actually have
        // it's changes applied (control text will still be added though)
        val configChangeBufferPeriod: Long = (2 * 60 * 1000)
    }

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

    private val isConfigForcedOn by lazy { TextSecurePreferences.hasForcedNewConfig(context) }

    private val listeners: MutableList<ConfigFactoryUpdateListener> = mutableListOf()
    fun registerListener(listener: ConfigFactoryUpdateListener) {
        listeners += listener
    }

    fun unregisterListener(listener: ConfigFactoryUpdateListener) {
        listeners -= listener
    }

    private inline fun <T> synchronizedWithLog(lock: Any, body: ()->T): T {
        Trace.beginSection("synchronizedWithLog")
        val result = synchronized(lock) {
            body()
        }
        Trace.endSection()
        return result
    }

    override val user: UserProfile?
        get() = synchronizedWithLog(userLock) {
            if (_userConfig == null) {
                val (secretKey, publicKey) = maybeGetUserInfo() ?: return null
                val userDump = configDatabase.retrieveConfigAndHashes(
                    SharedConfigMessage.Kind.USER_PROFILE.name,
                    publicKey
                )
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

    override val contacts: Contacts?
        get() = synchronizedWithLog(contactsLock) {
            if (_contacts == null) {
                val (secretKey, publicKey) = maybeGetUserInfo() ?: return null
                val contactsDump = configDatabase.retrieveConfigAndHashes(
                    SharedConfigMessage.Kind.CONTACTS.name,
                    publicKey
                )
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

    override val convoVolatile: ConversationVolatileConfig?
        get() = synchronizedWithLog(convoVolatileLock) {
            if (_convoVolatileConfig == null) {
                val (secretKey, publicKey) = maybeGetUserInfo() ?: return null
                val convoDump = configDatabase.retrieveConfigAndHashes(
                    SharedConfigMessage.Kind.CONVO_INFO_VOLATILE.name,
                    publicKey
                )
                _convoVolatileConfig = if (convoDump != null) {
                    ConversationVolatileConfig.newInstance(secretKey, convoDump)
                } else {
                    ConfigurationMessageUtilities.generateConversationVolatileDump(context)
                        ?.let { dump ->
                            ConversationVolatileConfig.newInstance(secretKey, dump)
                        } ?: ConversationVolatileConfig.newInstance(secretKey)
                }
            }
            _convoVolatileConfig
        }

    override val userGroups: UserGroupsConfig?
        get() = synchronizedWithLog(userGroupsLock) {
            if (_userGroups == null) {
                val (secretKey, publicKey) = maybeGetUserInfo() ?: return null
                val userGroupsDump = configDatabase.retrieveConfigAndHashes(
                    SharedConfigMessage.Kind.GROUPS.name,
                    publicKey
                )
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

    override fun getUserConfigs(): List<ConfigBase> =
        listOfNotNull(user, contacts, convoVolatile, userGroups)


    private fun persistUserConfigDump(timestamp: Long) = synchronized(userLock) {
        val dumped = user?.dump() ?: return
        val (_, publicKey) = maybeGetUserInfo() ?: return
        configDatabase.storeConfig(SharedConfigMessage.Kind.USER_PROFILE.name, publicKey, dumped, timestamp)
    }

    private fun persistContactsConfigDump(timestamp: Long) = synchronized(contactsLock) {
        val dumped = contacts?.dump() ?: return
        val (_, publicKey) = maybeGetUserInfo() ?: return
        configDatabase.storeConfig(SharedConfigMessage.Kind.CONTACTS.name, publicKey, dumped, timestamp)
    }

    private fun persistConvoVolatileConfigDump(timestamp: Long) = synchronized(convoVolatileLock) {
        val dumped = convoVolatile?.dump() ?: return
        val (_, publicKey) = maybeGetUserInfo() ?: return
        configDatabase.storeConfig(
            SharedConfigMessage.Kind.CONVO_INFO_VOLATILE.name,
            publicKey,
            dumped,
            timestamp
        )
    }

    private fun persistUserGroupsConfigDump(timestamp: Long) = synchronized(userGroupsLock) {
        val dumped = userGroups?.dump() ?: return
        val (_, publicKey) = maybeGetUserInfo() ?: return
        configDatabase.storeConfig(SharedConfigMessage.Kind.GROUPS.name, publicKey, dumped, timestamp)
    }

    override fun persist(forConfigObject: ConfigBase, timestamp: Long) {
        try {
            listeners.forEach { listener ->
                listener.notifyUpdates(forConfigObject, timestamp)
            }
            when (forConfigObject) {
                is UserProfile -> persistUserConfigDump(timestamp)
                is Contacts -> persistContactsConfigDump(timestamp)
                is ConversationVolatileConfig -> persistConvoVolatileConfigDump(timestamp)
                is UserGroupsConfig -> persistUserGroupsConfigDump(timestamp)
                else -> throw UnsupportedOperationException("Can't support type of ${forConfigObject::class.simpleName} yet")
            }
        } catch (e: Exception) {
            Log.e("Loki", "failed to persist ${forConfigObject.javaClass.simpleName}", e)
        }
    }

    override fun getConfigTimestamp(forConfigObject: ConfigBase, publicKey: String): Long {
        val variant = when (forConfigObject) {
            is UserProfile -> SharedConfigMessage.Kind.USER_PROFILE.name
            is Contacts -> SharedConfigMessage.Kind.CONTACTS.name
            is ConversationVolatileConfig -> SharedConfigMessage.Kind.CONVO_INFO_VOLATILE.name
            is UserGroupsConfig -> SharedConfigMessage.Kind.GROUPS.name
            else -> throw UnsupportedOperationException("Can't support type of ${forConfigObject::class.simpleName} yet")
        }

        return configDatabase.retrieveConfigLastUpdateTimestamp(variant, publicKey)
    }

    override fun conversationInConfig(
        publicKey: String?,
        groupPublicKey: String?,
        openGroupId: String?,
        visibleOnly: Boolean
    ): Boolean {
        val (_, userPublicKey) = maybeGetUserInfo() ?: return true

        if (openGroupId != null) {
            val userGroups = userGroups ?: return false
            val threadId = GroupManager.getOpenGroupThreadID(openGroupId, context)
            val openGroup = get(context).lokiThreadDatabase().getOpenGroupChat(threadId) ?: return false

            // Not handling the `hidden` behaviour for communities so just indicate the existence
            return (userGroups.getCommunityInfo(openGroup.server, openGroup.room) != null)
        }
        else if (groupPublicKey != null) {
            val userGroups = userGroups ?: return false

            // Not handling the `hidden` behaviour for legacy groups so just indicate the existence
            return (userGroups.getLegacyGroupInfo(groupPublicKey) != null)
        }
        else if (publicKey == userPublicKey) {
            val user = user ?: return false

            return (!visibleOnly || user.getNtsPriority() != ConfigBase.PRIORITY_HIDDEN)
        }
        else if (publicKey != null) {
            val contacts = contacts ?: return false
            val targetContact = contacts.get(publicKey) ?: return false

            return (!visibleOnly || targetContact.priority != ConfigBase.PRIORITY_HIDDEN)
        }

        return false
    }

    override fun canPerformChange(variant: String, publicKey: String, changeTimestampMs: Long): Boolean {
        val lastUpdateTimestampMs = configDatabase.retrieveConfigLastUpdateTimestamp(variant, publicKey)

        // Ensure the change occurred after the last config message was handled (minus the buffer period)
        return (changeTimestampMs >= (lastUpdateTimestampMs - ConfigFactory.configChangeBufferPeriod))
    }
}