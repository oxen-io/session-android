package org.thoughtcrime.securesms.dependencies

import android.content.Context
import android.os.Trace
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import network.loki.messenger.libsession_util.Config
import network.loki.messenger.libsession_util.ConfigBase
import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.ConversationVolatileConfig
import network.loki.messenger.libsession_util.GroupInfoConfig
import network.loki.messenger.libsession_util.GroupKeysConfig
import network.loki.messenger.libsession_util.GroupMembersConfig
import network.loki.messenger.libsession_util.UserGroupsConfig
import network.loki.messenger.libsession_util.UserProfile
import network.loki.messenger.libsession_util.util.Sodium
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.ConfigFactoryUpdateListener
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.protos.SignalServiceProtos.SharedConfigMessage
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.SessionId
import org.thoughtcrime.securesms.database.ConfigDatabase
import org.thoughtcrime.securesms.dependencies.DatabaseComponent.Companion.get
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities

class ConfigFactory(
    private val context: Context,
    private val configDatabase: ConfigDatabase,
    /** <ed25519 secret key,33 byte prefixed public key (hex encoded)> */
    private val maybeGetUserInfo: () -> Pair<ByteArray, String>?
) :
    ConfigFactoryProtocol {
    companion object {
        // This is a buffer period within which we will process messages which would result in a
        // config change, any message which would normally result in a config change which was sent
        // before `lastConfigMessage.timestamp - configChangeBufferPeriod` will not  actually have
        // it's changes applied (control text will still be added though)
        const val configChangeBufferPeriod: Long = (2 * 60 * 1000)
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

    private val _configUpdateNotifications = Channel<SessionId>()
    val configUpdateNotifications = _configUpdateNotifications.receiveAsFlow()

    fun registerListener(listener: ConfigFactoryUpdateListener) {
        listeners += listener
    }

    fun unregisterListener(listener: ConfigFactoryUpdateListener) {
        listeners -= listener
    }

    private inline fun <T> synchronizedWithLog(lock: Any, body: () -> T): T {
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

    private fun getGroupAuthInfo(groupSessionId: SessionId) = userGroups?.getClosedGroup(groupSessionId.hexString())?.let {
        it.adminKey to it.authData
    }

    override fun getGroupInfoConfig(groupSessionId: SessionId): GroupInfoConfig? = getGroupAuthInfo(groupSessionId)?.let { (sk, _) ->
        // get any potential initial dumps
        val dump = configDatabase.retrieveConfigAndHashes(
            ConfigDatabase.INFO_VARIANT,
            groupSessionId.hexString()
        ) ?: byteArrayOf()

        GroupInfoConfig.newInstance(Hex.fromStringCondensed(groupSessionId.publicKey), sk, dump)
    }

    override fun getGroupKeysConfig(groupSessionId: SessionId,
                                    info: GroupInfoConfig?,
                                    members: GroupMembersConfig?,
                                    free: Boolean): GroupKeysConfig? = getGroupAuthInfo(groupSessionId)?.let { (sk, _) ->
        // Get the user info or return early
        val (userSk, _) = maybeGetUserInfo() ?: return@let null

        // Get the group info or return early
        val usedInfo = info ?: getGroupInfoConfig(groupSessionId) ?: return@let null

        // Get the group members or return early
        val usedMembers = members ?: getGroupMemberConfig(groupSessionId) ?: return@let null

        // Get the dump or empty
        val dump = configDatabase.retrieveConfigAndHashes(
            ConfigDatabase.KEYS_VARIANT,
            groupSessionId.hexString()
        ) ?: byteArrayOf()

        // Put it all together
        val keys = GroupKeysConfig.newInstance(
            userSk,
            Hex.fromStringCondensed(groupSessionId.publicKey),
            sk,
            dump,
            usedInfo,
            usedMembers
        )
        if (free) {
            info?.free()
            members?.free()
        }
        if (usedInfo !== info) usedInfo.free()
        if (usedMembers !== members) usedMembers.free()
        keys
    }

    override fun getGroupMemberConfig(groupSessionId: SessionId): GroupMembersConfig? = getGroupAuthInfo(groupSessionId)?.let { (sk, _) ->
        // Get initial dump if we have one
        val dump = configDatabase.retrieveConfigAndHashes(
            ConfigDatabase.MEMBER_VARIANT,
            groupSessionId.hexString()
        ) ?: byteArrayOf()

        GroupMembersConfig.newInstance(
            Hex.fromStringCondensed(groupSessionId.publicKey),
            sk,
            dump
        )
    }

    override fun constructGroupKeysConfig(
        groupSessionId: SessionId,
        info: GroupInfoConfig,
        members: GroupMembersConfig
    ): GroupKeysConfig? = getGroupAuthInfo(groupSessionId)?.let { (sk, _) ->
        val (userSk, _) = maybeGetUserInfo() ?: return null
        GroupKeysConfig.newInstance(
            userSk,
            Hex.fromStringCondensed(groupSessionId.publicKey),
            sk,
            info = info,
            members = members
        )
    }

    override fun userSessionId(): SessionId? {
        return maybeGetUserInfo()?.second?.let(SessionId::from)
    }

    override fun maybeDecryptForUser(encoded: ByteArray, domain: String, closedGroupSessionId: SessionId): ByteArray? {
        val secret = maybeGetUserInfo()?.first ?: run {
            Log.e("ConfigFactory", "No user ed25519 secret key decrypting a message for us")
            return null
        }
        return Sodium.decryptForMultipleSimple(
            encoded = encoded,
            ed25519SecretKey = secret,
            domain = domain,
            senderPubKey = Sodium.ed25519PkToCurve25519(closedGroupSessionId.pubKeyBytes)
        )
    }

    override fun getUserConfigs(): List<ConfigBase> =
        listOfNotNull(user, contacts, convoVolatile, userGroups)


    private fun persistUserConfigDump(timestamp: Long) = synchronized(userLock) {
        val dumped = user?.dump() ?: return
        val (_, publicKey) = maybeGetUserInfo() ?: return
        configDatabase.storeConfig(
            SharedConfigMessage.Kind.USER_PROFILE.name,
            publicKey,
            dumped,
            timestamp
        )
    }

    private fun persistContactsConfigDump(timestamp: Long) = synchronized(contactsLock) {
        val dumped = contacts?.dump() ?: return
        val (_, publicKey) = maybeGetUserInfo() ?: return
        configDatabase.storeConfig(
            SharedConfigMessage.Kind.CONTACTS.name,
            publicKey,
            dumped,
            timestamp
        )
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
        configDatabase.storeConfig(
            SharedConfigMessage.Kind.GROUPS.name,
            publicKey,
            dumped,
            timestamp
        )
    }

    fun persistGroupConfigDump(forConfigObject: ConfigBase, groupSessionId: SessionId, timestamp: Long) = synchronized(userGroupsLock) {
        val dumped = forConfigObject.dump()
        val variant = when (forConfigObject) {
            is GroupMembersConfig -> ConfigDatabase.MEMBER_VARIANT
            is GroupInfoConfig -> ConfigDatabase.INFO_VARIANT
            else -> throw Exception("Shouldn't be called")
        }
        configDatabase.storeConfig(
            variant,
            groupSessionId.hexString(),
            dumped,
            timestamp
        )
        _configUpdateNotifications.trySend(groupSessionId)
    }

    override fun persist(forConfigObject: Config, timestamp: Long, forPublicKey: String?) {
        try {
            listeners.forEach { listener ->
                listener.notifyUpdates(forConfigObject, timestamp)
            }
            if (forConfigObject is ConfigBase && !forConfigObject.needsDump() || forConfigObject is GroupKeysConfig && !forConfigObject.needsDump()) {
                Log.d("ConfigFactory", "Don't need to persist ${forConfigObject.javaClass} for $forPublicKey pubkey")
                return
            }
            when (forConfigObject) {
                is UserProfile -> persistUserConfigDump(timestamp)
                is Contacts -> persistContactsConfigDump(timestamp)
                is ConversationVolatileConfig -> persistConvoVolatileConfigDump(timestamp)
                is UserGroupsConfig -> persistUserGroupsConfigDump(timestamp)
                is GroupMembersConfig -> persistGroupConfigDump(forConfigObject, SessionId.from(forPublicKey!!), timestamp)
                is GroupInfoConfig -> persistGroupConfigDump(forConfigObject, SessionId.from(forPublicKey!!), timestamp)
                else -> throw UnsupportedOperationException("Can't support type of ${forConfigObject::class.simpleName} yet")
            }
        } catch (e: Exception) {
            Log.e("Loki", "failed to persist ${forConfigObject.javaClass.simpleName}", e)
        }
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
            val openGroup =
                get(context).lokiThreadDatabase().getOpenGroupChat(threadId) ?: return false

            // Not handling the `hidden` behaviour for communities so just indicate the existence
            return (userGroups.getCommunityInfo(openGroup.server, openGroup.room) != null)
        } else if (groupPublicKey != null) {
            val userGroups = userGroups ?: return false

            // Not handling the `hidden` behaviour for legacy groups so just indicate the existence
            return if (groupPublicKey.startsWith(IdPrefix.GROUP.value)) {
                userGroups.getClosedGroup(groupPublicKey) != null
            } else {
                userGroups.getLegacyGroupInfo(groupPublicKey) != null
            }
        } else if (publicKey == userPublicKey) {
            val user = user ?: return false

            return (!visibleOnly || user.getNtsPriority() != ConfigBase.PRIORITY_HIDDEN)
        } else if (publicKey != null) {
            val contacts = contacts ?: return false
            val targetContact = contacts.get(publicKey) ?: return false

            return (!visibleOnly || targetContact.priority != ConfigBase.PRIORITY_HIDDEN)
        }

        return false
    }

    override fun canPerformChange(
        variant: String,
        publicKey: String,
        changeTimestampMs: Long
    ): Boolean {
        val lastUpdateTimestampMs =
            configDatabase.retrieveConfigLastUpdateTimestamp(variant, publicKey)

        // Ensure the change occurred after the last config message was handled (minus the buffer period)
        return (changeTimestampMs >= (lastUpdateTimestampMs - configChangeBufferPeriod))
    }

    override fun saveGroupConfigs(
        groupKeys: GroupKeysConfig,
        groupInfo: GroupInfoConfig,
        groupMembers: GroupMembersConfig
    ) {
        val pubKey = groupInfo.id().hexString()
        val timestamp = SnodeAPI.nowWithOffset

        // this would be nicer with a .any iteration or something but the base types don't line up
        val anyNeedDump = groupKeys.needsDump() || groupInfo.needsDump() || groupMembers.needsDump()
        if (!anyNeedDump) return Log.d("ConfigFactory", "Group config doesn't need dump, skipping")
        else Log.d("ConfigFactory", "Group config needs dump, storing and notifying")

        configDatabase.storeGroupConfigs(pubKey, groupKeys.dump(), groupInfo.dump(), groupMembers.dump(), timestamp)
        _configUpdateNotifications.trySend(groupInfo.id())
    }

    override fun removeGroup(closedGroupId: SessionId) {
        val groups = userGroups ?: return
        groups.eraseClosedGroup(closedGroupId.hexString())
        persist(groups, SnodeAPI.nowWithOffset)
        configDatabase.deleteGroupConfigs(closedGroupId)
    }

    override fun scheduleUpdate(destination: Destination) {
        // there's probably a better way to do this
        ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(destination)
    }
}