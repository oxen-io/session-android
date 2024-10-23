package org.thoughtcrime.securesms.configs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import network.loki.messenger.libsession_util.ConfigBase.Companion.PRIORITY_HIDDEN
import network.loki.messenger.libsession_util.ConfigBase.Companion.PRIORITY_PINNED
import network.loki.messenger.libsession_util.ReadableGroupInfoConfig
import network.loki.messenger.libsession_util.ReadableUserGroupsConfig
import network.loki.messenger.libsession_util.ReadableUserProfile
import network.loki.messenger.libsession_util.util.BaseCommunityInfo
import network.loki.messenger.libsession_util.util.Contact
import network.loki.messenger.libsession_util.util.Conversation
import network.loki.messenger.libsession_util.util.ExpiryMode
import network.loki.messenger.libsession_util.util.GroupInfo
import network.loki.messenger.libsession_util.util.UserPic
import network.loki.messenger.libsession_util.util.afterSend
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.jobs.BackgroundGroupAddJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.sending_receiving.notifications.PushRegistryV1
import org.session.libsession.messaging.sending_receiving.pollers.LegacyClosedGroupPollerV2
import org.session.libsession.snode.SnodeClock
import org.session.libsession.utilities.Address.Companion.fromSerialized
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.ConfigUpdateNotification
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.SSKEnvironment.ProfileManagerProtocol.Companion.NAME_PADDED_LENGTH
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.UserConfigType
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.crypto.ecc.DjbECPrivateKey
import org.session.libsignal.crypto.ecc.DjbECPublicKey
import org.session.libsignal.crypto.ecc.ECKeyPair
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.database.MmsDatabase
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.dependencies.PollerFactory
import org.thoughtcrime.securesms.groups.ClosedGroupManager
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.sskenvironment.ProfileManager
import javax.inject.Inject

private const val TAG = "ConfigToDatabaseSync"

/**
 * This class is responsible for syncing config system's data into the database.
 *
 * It does so by listening to the [ConfigFactoryProtocol.configUpdateNotifications] and updating the database accordingly.
 *
 * @see ConfigUploader For upload config system data into swarm automagically.
 */
class ConfigToDatabaseSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configFactory: ConfigFactoryProtocol,
    private val storage: StorageProtocol,
    private val threadDatabase: ThreadDatabase,
    private val recipientDatabase: RecipientDatabase,
    private val mmsDatabase: MmsDatabase,
    private val pollerFactory: PollerFactory,
    private val clock: SnodeClock,
    private val profileManager: ProfileManager,
    private val preferences: TextSecurePreferences,
) {
    private var job: Job? = null

    fun start() {
        require(job == null) { "Already started" }

        @Suppress("OPT_IN_USAGE")
        job = GlobalScope.launch {
            supervisorScope {
                val job1 = async {
                    configFactory.configUpdateNotifications
                        .filterIsInstance<ConfigUpdateNotification.UserConfigsMerged>()
                        .debounce(800L)
                        .collect { config ->
                            try {
                                Log.i(TAG, "Start syncing user configs")
                                syncUserConfigs(config.configType, config.timestamp)
                                Log.i(TAG, "Finished syncing user configs")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error syncing user configs", e)
                            }
                        }
                }

                val job2 = async {
                    configFactory.configUpdateNotifications
                        .filterIsInstance<ConfigUpdateNotification.GroupConfigsUpdated>()
                        .collect {
                            syncGroupConfigs(it.groupId)
                        }
                }

                job1.await()
                job2.await()
            }
        }
    }

    private fun syncGroupConfigs(groupId: AccountId) {
        val info = configFactory.withGroupConfigs(groupId) {
            UpdateGroupInfo(it.groupInfo)
        }

        updateGroup(info)
    }

    private fun syncUserConfigs(userConfigType: UserConfigType, updateTimestamp: Long) {
        val configUpdate = configFactory.withUserConfigs { configs ->
            when (userConfigType) {
                UserConfigType.USER_PROFILE -> UpdateUserInfo(configs.userProfile)
                UserConfigType.USER_GROUPS -> UpdateUserGroupsInfo(configs.userGroups)
                UserConfigType.CONTACTS -> UpdateContacts(configs.contacts.all())
                UserConfigType.CONVO_INFO_VOLATILE -> UpdateConvoVolatile(configs.convoInfoVolatile.all())
            }
        }

        when (configUpdate) {
            is UpdateUserInfo -> updateUser(configUpdate, updateTimestamp)
            is UpdateUserGroupsInfo -> updateUserGroups(configUpdate, updateTimestamp)
            is UpdateContacts -> updateContacts(configUpdate, updateTimestamp)
            is UpdateConvoVolatile -> updateConvoVolatile(configUpdate)
            else -> error("Unknown config update type: $configUpdate")
        }
    }

    private data class UpdateUserInfo(
        val name: String?,
        val userPic: UserPic,
        val ntsPriority: Long,
        val ntsExpiry: ExpiryMode
    ) {
        constructor(profile: ReadableUserProfile) : this(
            name = profile.getName(),
            userPic = profile.getPic(),
            ntsPriority = profile.getNtsPriority(),
            ntsExpiry = profile.getNtsExpiry()
        )
    }

    private fun updateUser(userProfile: UpdateUserInfo, messageTimestamp: Long) {
        val userPublicKey = storage.getUserPublicKey() ?: return
        // would love to get rid of recipient and context from this
        val recipient = Recipient.from(context, fromSerialized(userPublicKey), false)

        // Update profile name
        userProfile.name?.takeUnless { it.isEmpty() }?.truncate(NAME_PADDED_LENGTH)?.let {
            preferences.setProfileName(it)
            profileManager.setName(context, recipient, it)
        }

        // Update profile picture
        if (userProfile.userPic == UserPic.DEFAULT) {
            storage.clearUserPic(clearConfig = false)
        } else if (userProfile.userPic.key.isNotEmpty() && userProfile.userPic.url.isNotEmpty()
            && preferences.getProfilePictureURL() != userProfile.userPic.url
        ) {
            storage.setUserProfilePicture(userProfile.userPic.url, userProfile.userPic.key)
        }

        if (userProfile.ntsPriority == PRIORITY_HIDDEN) {
            // delete nts thread if needed
            val ourThread = storage.getThreadId(recipient) ?: return
            storage.deleteConversation(ourThread)
        } else {
            // create note to self thread if needed (?)
            val address = recipient.address
            val ourThread = storage.getThreadId(address) ?: storage.getOrCreateThreadIdFor(address).also {
                storage.setThreadDate(it, 0)
            }
            threadDatabase.setHasSent(ourThread, true)
            storage.setPinned(ourThread, userProfile.ntsPriority > 0)
        }

        // Set or reset the shared library to use latest expiration config
        storage.getThreadId(recipient)?.let {
            storage.setExpirationConfiguration(
                storage.getExpirationConfiguration(it)?.takeIf { it.updatedTimestampMs > messageTimestamp } ?:
                ExpirationConfiguration(it, userProfile.ntsExpiry, messageTimestamp)
            )
        }
    }

    private data class UpdateGroupInfo(
        val id: AccountId,
        val name: String?,
        val destroyed: Boolean,
        val deleteBefore: Long?,
        val deleteAttachmentsBefore: Long?
    ) {
        constructor(groupInfoConfig: ReadableGroupInfoConfig) : this(
            id = groupInfoConfig.id(),
            name = groupInfoConfig.getName(),
            destroyed = groupInfoConfig.isDestroyed(),
            deleteBefore = groupInfoConfig.getDeleteBefore(),
            deleteAttachmentsBefore = groupInfoConfig.getDeleteAttachmentsBefore()
        )
    }

    private fun updateGroup(groupInfoConfig: UpdateGroupInfo) {
        val threadId = storage.getThreadId(fromSerialized(groupInfoConfig.id.hexString)) ?: return
        val recipient = storage.getRecipientForThread(threadId) ?: return
        recipientDatabase.setProfileName(recipient, groupInfoConfig.name)
        profileManager.setName(context, recipient, groupInfoConfig.name ?: "")

        if (groupInfoConfig.destroyed) {
            storage.clearMessages(threadId)
        } else {
            groupInfoConfig.deleteBefore?.let { removeBefore ->
                storage.trimThreadBefore(threadId, removeBefore)
            }
            groupInfoConfig.deleteAttachmentsBefore?.let { removeAttachmentsBefore ->
                mmsDatabase.deleteMessagesInThreadBeforeDate(threadId, removeAttachmentsBefore, onlyMedia = true)
            }
        }
    }

    private data class UpdateContacts(val contacts: List<Contact>)

    private fun updateContacts(contacts: UpdateContacts, messageTimestamp: Long) {
        storage.addLibSessionContacts(contacts.contacts, messageTimestamp)
    }

    private data class UpdateUserGroupsInfo(
        val communityInfo: List<GroupInfo.CommunityGroupInfo>,
        val legacyGroupInfo: List<GroupInfo.LegacyGroupInfo>,
        val closedGroupInfo: List<GroupInfo.ClosedGroupInfo>
    ) {
        constructor(userGroups: ReadableUserGroupsConfig) : this(
            communityInfo = userGroups.allCommunityInfo(),
            legacyGroupInfo = userGroups.allLegacyGroupInfo(),
            closedGroupInfo = userGroups.allClosedGroupInfo()
        )
    }

    private fun updateUserGroups(userGroups: UpdateUserGroupsInfo, messageTimestamp: Long) {
        val localUserPublicKey = storage.getUserPublicKey() ?: return Log.w(
            TAG,
            "No user public key when trying to update user groups from config"
        )
        val allOpenGroups = storage.getAllOpenGroups()
        val toDeleteCommunities = allOpenGroups.filter {
            Conversation.Community(BaseCommunityInfo(it.value.server, it.value.room, it.value.publicKey), 0, false).baseCommunityInfo.fullUrl() !in userGroups.communityInfo.map { it.community.fullUrl() }
        }

        val existingCommunities: Map<Long, OpenGroup> = allOpenGroups.filterKeys { it !in toDeleteCommunities.keys }
        val toAddCommunities = userGroups.communityInfo.filter { it.community.fullUrl() !in existingCommunities.map { it.value.joinURL } }
        val existingJoinUrls = existingCommunities.values.map { it.joinURL }

        val existingLegacyClosedGroups = storage.getAllGroups(includeInactive = true).filter { it.isLegacyClosedGroup }
        val lgcIds = userGroups.legacyGroupInfo.map { it.accountId }
        val toDeleteLegacyClosedGroups = existingLegacyClosedGroups.filter { group ->
            GroupUtil.doubleDecodeGroupId(group.encodedId) !in lgcIds
        }

        // delete the ones which are not listed in the config
        toDeleteCommunities.values.forEach { openGroup ->
            OpenGroupManager.delete(openGroup.server, openGroup.room, context)
        }

        toDeleteLegacyClosedGroups.forEach { deleteGroup ->
            val threadId = storage.getThreadId(deleteGroup.encodedId)
            if (threadId != null) {
                ClosedGroupManager.silentlyRemoveGroup(context,threadId,
                    GroupUtil.doubleDecodeGroupId(deleteGroup.encodedId), deleteGroup.encodedId, localUserPublicKey, delete = true)
            }
        }

        toAddCommunities.forEach { toAddCommunity ->
            val joinUrl = toAddCommunity.community.fullUrl()
            if (!storage.hasBackgroundGroupAddJob(joinUrl)) {
                JobQueue.shared.add(BackgroundGroupAddJob(joinUrl))
            }
        }

        for (groupInfo in userGroups.communityInfo) {
            val groupBaseCommunity = groupInfo.community
            if (groupBaseCommunity.fullUrl() in existingJoinUrls) {
                // add it
                val (threadId, _) = existingCommunities.entries.first { (_, v) -> v.joinURL == groupInfo.community.fullUrl() }
                threadDatabase.setPinned(threadId, groupInfo.priority == PRIORITY_PINNED)
            }
        }

        val existingClosedGroupThreads: Map<AccountId, Long> = threadDatabase.readerFor(threadDatabase.conversationList).use { reader ->
            buildMap(reader.count) {
                var current = reader.next
                while (current != null) {
                    if (current.recipient?.isClosedGroupV2Recipient == true) {
                        put(AccountId(current.recipient.address.serialize()), current.threadId)
                    }

                    current = reader.next
                }
            }
        }

        val groupThreadsToKeep = hashMapOf<AccountId, Long>()

        for (closedGroup in userGroups.closedGroupInfo) {
            val recipient = Recipient.from(context, fromSerialized(closedGroup.groupAccountId.hexString), false)
            storage.setRecipientApprovedMe(recipient, true)
            storage.setRecipientApproved(recipient, !closedGroup.invited)
            val threadId = storage.getOrCreateThreadIdFor(recipient.address)
            groupThreadsToKeep[closedGroup.groupAccountId] = threadId

            storage.setPinned(threadId, closedGroup.priority == PRIORITY_PINNED)
            if (!closedGroup.invited) {
                pollerFactory.pollerFor(closedGroup.groupAccountId)?.start()
            }
        }

        val toRemove = existingClosedGroupThreads - groupThreadsToKeep.keys
        Log.d(TAG, "Removing ${toRemove.size} closed groups")
        toRemove.forEach { (groupId, threadId) ->
            pollerFactory.pollerFor(groupId)?.stop()
            storage.removeClosedGroupThread(threadId)
        }

        for (group in userGroups.legacyGroupInfo) {
            val groupId = GroupUtil.doubleEncodeGroupID(group.accountId)
            val existingGroup = existingLegacyClosedGroups.firstOrNull { GroupUtil.doubleDecodeGroupId(it.encodedId) == group.accountId }
            val existingThread = existingGroup?.let { storage.getThreadId(existingGroup.encodedId) }
            if (existingGroup != null) {
                if (group.priority == PRIORITY_HIDDEN && existingThread != null) {
                    ClosedGroupManager.silentlyRemoveGroup(context,existingThread,
                        GroupUtil.doubleDecodeGroupId(existingGroup.encodedId), existingGroup.encodedId, localUserPublicKey, delete = true)
                } else if (existingThread == null) {
                    Log.w(TAG, "Existing group had no thread to hide")
                } else {
                    Log.d(TAG, "Setting existing group pinned status to ${group.priority}")
                    threadDatabase.setPinned(existingThread, group.priority == PRIORITY_PINNED)
                }
            } else {
                val members = group.members.keys.map { fromSerialized(it) }
                val admins = group.members.filter { it.value /*admin = true*/ }.keys.map { fromSerialized(it) }
                val title = group.name
                val formationTimestamp = (group.joinedAt * 1000L)
                storage.createGroup(groupId, title, admins + members, null, null, admins, formationTimestamp)
                storage.setProfileSharing(fromSerialized(groupId), true)
                // Add the group to the user's set of public keys to poll for
                storage.addClosedGroupPublicKey(group.accountId)
                // Store the encryption key pair
                val keyPair = ECKeyPair(DjbECPublicKey(group.encPubKey), DjbECPrivateKey(group.encSecKey))
                storage.addClosedGroupEncryptionKeyPair(keyPair, group.accountId, clock.currentTimeMills())
                // Notify the PN server
                PushRegistryV1.subscribeGroup(group.accountId, publicKey = localUserPublicKey)
                // Notify the user
                val threadID = storage.getOrCreateThreadIdFor(fromSerialized(groupId))
                threadDatabase.setDate(threadID, formationTimestamp)

                // Note: Commenting out this line prevents the timestamp of room creation being added to a new closed group,
                // which in turn allows us to show the `groupNoMessages` control message text.
                //insertOutgoingInfoMessage(context, groupId, SignalServiceGroup.Type.CREATION, title, members.map { it.serialize() }, admins.map { it.serialize() }, threadID, formationTimestamp)

                // Don't create config group here, it's from a config update
                // Start polling
                LegacyClosedGroupPollerV2.shared.startPolling(group.accountId)
            }
            storage.getThreadId(fromSerialized(groupId))?.let {
                storage.setExpirationConfiguration(
                    storage.getExpirationConfiguration(it)?.takeIf { it.updatedTimestampMs > messageTimestamp }
                        ?: ExpirationConfiguration(it, afterSend(group.disappearingTimer), messageTimestamp)
                )
            }
        }
    }

    private data class UpdateConvoVolatile(val convos: List<Conversation?>)

    private fun updateConvoVolatile(convos: UpdateConvoVolatile) {
        val extracted = convos.convos.filterNotNull()
        for (conversation in extracted) {
            val threadId = when (conversation) {
                is Conversation.OneToOne -> storage.getThreadIdFor(conversation.accountId, null, null, createThread = false)
                is Conversation.LegacyGroup -> storage.getThreadIdFor("", conversation.groupId,null, createThread = false)
                is Conversation.Community -> storage.getThreadIdFor("",null, "${conversation.baseCommunityInfo.baseUrl.removeSuffix("/")}.${conversation.baseCommunityInfo.room}", createThread = false)
                is Conversation.ClosedGroup -> storage.getThreadIdFor(conversation.accountId, null, null, createThread = false) // New groups will be managed bia libsession
            }
            if (threadId != null) {
                if (conversation.lastRead > storage.getLastSeen(threadId)) {
                    storage.markConversationAsRead(threadId, conversation.lastRead, force = true)
                    storage.updateThread(threadId, false)
                }
            }
        }
    }
}

/**
 * Truncate a string to a specified number of bytes
 *
 * This could split multi-byte characters/emojis.
 */
private fun String.truncate(sizeInBytes: Int): String =
    toByteArray().takeIf { it.size > sizeInBytes }?.take(sizeInBytes)?.toByteArray()?.let(::String) ?: this
