package org.thoughtcrime.securesms.util

import android.content.Context
import network.loki.messenger.libsession_util.ConfigBase
import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.ConversationVolatileConfig
import network.loki.messenger.libsession_util.UserGroupsConfig
import network.loki.messenger.libsession_util.UserProfile
import network.loki.messenger.libsession_util.util.BaseCommunityInfo
import network.loki.messenger.libsession_util.util.Contact
import network.loki.messenger.libsession_util.util.ExpiryMode
import network.loki.messenger.libsession_util.util.GroupInfo
import network.loki.messenger.libsession_util.util.UserPic
import nl.komponents.kovenant.Promise
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.ConfigurationSyncJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.control.ConfigurationMessage
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.WindowDebouncer
import org.session.libsignal.crypto.ecc.DjbECPublicKey
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import java.util.Timer

object ConfigurationMessageUtilities {

    private val debouncer = WindowDebouncer(3000, Timer())

    private fun scheduleConfigSync(userPublicKey: String) {
        debouncer.publish {
            // don't schedule job if we already have one
            val storage = MessagingModuleConfiguration.shared.storage
            val ourDestination = Destination.Contact(userPublicKey)
            val currentStorageJob = storage.getConfigSyncJob(ourDestination)
            if (currentStorageJob != null) {
                (currentStorageJob as ConfigurationSyncJob).shouldRunAgain.set(true)
                return@publish
            }
            val newConfigSync = ConfigurationSyncJob(ourDestination)
            JobQueue.shared.add(newConfigSync)
        }
    }

    @JvmStatic
    fun syncConfigurationIfNeeded(context: Context) {
        // add if check here to schedule new config job process and return early
        val userPublicKey = TextSecurePreferences.getLocalNumber(context) ?: return
        val forcedConfig = TextSecurePreferences.hasForcedNewConfig(context)
        val currentTime = SnodeAPI.nowWithOffset
        if (ConfigBase.isNewConfigEnabled(forcedConfig, currentTime)) {
            scheduleConfigSync(userPublicKey)
            return
        }
        val lastSyncTime = TextSecurePreferences.getLastConfigurationSyncTime(context)
        val now = System.currentTimeMillis()
        if (now - lastSyncTime < 7 * 24 * 60 * 60 * 1000) return
        val contacts = ContactUtilities.getAllContacts(context).filter { recipient ->
            !recipient.name.isNullOrEmpty() && !recipient.isLocalNumber && recipient.address.serialize().isNotEmpty()
        }.map { recipient ->
            ConfigurationMessage.Contact(
                publicKey = recipient.address.serialize(),
                name = recipient.name!!,
                profilePicture = recipient.profileAvatar,
                profileKey = recipient.profileKey,
                isApproved = recipient.isApproved,
                isBlocked = recipient.isBlocked,
                didApproveMe = recipient.hasApprovedMe()
            )
        }
        val configurationMessage = ConfigurationMessage.getCurrent(contacts) ?: return
        MessageSender.send(configurationMessage, Address.fromSerialized(userPublicKey))
        TextSecurePreferences.setLastConfigurationSyncTime(context, now)
    }

    fun forceSyncConfigurationNowIfNeeded(context: Context): Promise<Unit, Exception> {
        // add if check here to schedule new config job process and return early
        val userPublicKey = TextSecurePreferences.getLocalNumber(context) ?: return Promise.ofFail(NullPointerException("User Public Key is null"))
        val forcedConfig = TextSecurePreferences.hasForcedNewConfig(context)
        val currentTime = SnodeAPI.nowWithOffset
        if (ConfigBase.isNewConfigEnabled(forcedConfig, currentTime)) {
            // schedule job if none exist
            // don't schedule job if we already have one
            scheduleConfigSync(userPublicKey)
            return Promise.ofSuccess(Unit)
        }
        val contacts = ContactUtilities.getAllContacts(context).filter { recipient ->
            !recipient.isGroupRecipient && !recipient.name.isNullOrEmpty() && !recipient.isLocalNumber && recipient.address.serialize().isNotEmpty()
        }.map { recipient ->
            ConfigurationMessage.Contact(
                publicKey = recipient.address.serialize(),
                name = recipient.name!!,
                profilePicture = recipient.profileAvatar,
                profileKey = recipient.profileKey,
                isApproved = recipient.isApproved,
                isBlocked = recipient.isBlocked,
                didApproveMe = recipient.hasApprovedMe()
            )
        }
        val configurationMessage = ConfigurationMessage.getCurrent(contacts) ?: return Promise.ofSuccess(Unit)
        val promise = MessageSender.send(configurationMessage, Destination.from(Address.fromSerialized(userPublicKey)), isSyncMessage = true)
        TextSecurePreferences.setLastConfigurationSyncTime(context, System.currentTimeMillis())
        return promise
    }

    private fun maybeUserSecretKey() = MessagingModuleConfiguration.shared.getUserED25519KeyPair()?.secretKey?.asBytes

    fun generateUserProfileConfigDump(): ByteArray? {
        val storage = MessagingModuleConfiguration.shared.storage
        val ownPublicKey = storage.getUserPublicKey() ?: return null
        val config = ConfigurationMessage.getCurrent(listOf()) ?: return null
        val secretKey = maybeUserSecretKey() ?: return null
        val profile = UserProfile.newInstance(secretKey)
        profile.setName(config.displayName)
        val picUrl = config.profilePicture
        val picKey = config.profileKey
        if (!picUrl.isNullOrEmpty() && picKey.isNotEmpty()) {
            profile.setPic(UserPic(picUrl, picKey))
        }
        val ownThreadId = storage.getThreadId(Address.fromSerialized(ownPublicKey))
        profile.setNtsPriority(
            if (ownThreadId != null)
                if (storage.isPinned(ownThreadId)) ConfigBase.PRIORITY_PINNED else ConfigBase.PRIORITY_VISIBLE
            else ConfigBase.PRIORITY_HIDDEN
        )
        val dump = profile.dump()
        profile.free()
        return dump
    }

    fun generateContactConfigDump(): ByteArray? {
        val secretKey = maybeUserSecretKey() ?: return null
        val storage = MessagingModuleConfiguration.shared.storage
        val localUserKey = storage.getUserPublicKey() ?: return null
        val contactsWithSettings = storage.getAllContacts().filter { recipient ->
            recipient.sessionID != localUserKey && recipient.sessionID.startsWith(IdPrefix.STANDARD.value)
                    && storage.getThreadId(recipient.sessionID) != null
        }.map { contact ->
            val address = Address.fromSerialized(contact.sessionID)
            val thread = storage.getThreadId(address)
            val isPinned = if (thread != null) {
                storage.isPinned(thread)
            } else false

            Triple(contact, storage.getRecipientSettings(address)!!, isPinned)
        }
        val contactConfig = Contacts.newInstance(secretKey)
        for ((contact, settings, isPinned) in contactsWithSettings) {
            val url = contact.profilePictureURL
            val key = contact.profilePictureEncryptionKey
            val userPic = if (url.isNullOrEmpty() || key?.isNotEmpty() != true) {
                null
            } else {
                UserPic(url, key)
            }

            val contactInfo = Contact(
                id = contact.sessionID,
                name = contact.name.orEmpty(),
                nickname = contact.nickname.orEmpty(),
                blocked = settings.isBlocked,
                approved = settings.isApproved,
                approvedMe = settings.hasApprovedMe(),
                profilePicture = userPic ?: UserPic.DEFAULT,
                priority = if (isPinned) 1 else 0,
                expiryMode = if (settings.expireMessages == 0) ExpiryMode.NONE else ExpiryMode.AfterRead(settings.expireMessages.toLong())
            )
            contactConfig.set(contactInfo)
        }
        val dump = contactConfig.dump()
        contactConfig.free()
        if (dump.isEmpty()) return null
        return dump
    }

    fun generateConversationVolatileDump(context: Context): ByteArray? {
        val secretKey = maybeUserSecretKey() ?: return null
        val storage = MessagingModuleConfiguration.shared.storage
        val convoConfig = ConversationVolatileConfig.newInstance(secretKey)
        val threadDb = DatabaseComponent.get(context).threadDatabase()
        threadDb.approvedConversationList.use { cursor ->
            val reader = threadDb.readerFor(cursor)
            var current = reader.next
            while (current != null) {
                val recipient = current.recipient
                val contact = when {
                    recipient.isOpenGroupRecipient -> {
                        val openGroup = storage.getOpenGroup(current.threadId) ?: continue
                        val (base, room, pubKey) = BaseCommunityInfo.parseFullUrl(openGroup.joinURL) ?: continue
                        convoConfig.getOrConstructCommunity(base, room, pubKey)
                    }
                    recipient.isClosedGroupRecipient -> {
                        val groupPublicKey = GroupUtil.doubleDecodeGroupId(recipient.address.serialize())
                        convoConfig.getOrConstructLegacyGroup(groupPublicKey)
                    }
                    recipient.isContactRecipient -> {
                        if (recipient.isLocalNumber) null // this is handled by the user profile NTS data
                        else if (recipient.isOpenGroupInboxRecipient) null // specifically exclude
                        else if (!recipient.address.serialize().startsWith(IdPrefix.STANDARD.value)) null
                        else convoConfig.getOrConstructOneToOne(recipient.address.serialize())
                    }
                    else -> null
                }
                if (contact == null) {
                    current = reader.next
                    continue
                }
                contact.lastRead = current.lastSeen
                contact.unread = false
                convoConfig.set(contact)
                current = reader.next
            }
        }

        val dump = convoConfig.dump()
        convoConfig.free()
        if (dump.isEmpty()) return null
        return dump
    }

    fun generateUserGroupDump(context: Context): ByteArray? {
        val secretKey = maybeUserSecretKey() ?: return null
        val storage = MessagingModuleConfiguration.shared.storage
        val groupConfig = UserGroupsConfig.newInstance(secretKey)
        val allOpenGroups = storage.getAllOpenGroups().values.mapNotNull { openGroup ->
            val (baseUrl, room, pubKey) = BaseCommunityInfo.parseFullUrl(openGroup.joinURL) ?: return@mapNotNull null
            val pubKeyHex = Hex.toStringCondensed(pubKey)
            val baseInfo = BaseCommunityInfo(baseUrl, room, pubKeyHex)
            val threadId = storage.getThreadId(openGroup) ?: return@mapNotNull null
            val isPinned = storage.isPinned(threadId)
            GroupInfo.CommunityGroupInfo(baseInfo, if (isPinned) 1 else 0)
        }

        val allLgc = storage.getAllGroups(includeInactive = false).filter {
            it.isClosedGroup && it.isActive && it.members.size > 1
        }.mapNotNull { group ->
            val groupAddress = Address.fromSerialized(group.encodedId)
            val groupPublicKey = GroupUtil.doubleDecodeGroupID(groupAddress.serialize()).toHexString()
            val recipient = storage.getRecipientSettings(groupAddress) ?: return@mapNotNull null
            val encryptionKeyPair = storage.getLatestClosedGroupEncryptionKeyPair(groupPublicKey) ?: return@mapNotNull null
            val threadId = storage.getThreadId(group.encodedId)
            val isPinned = threadId?.let { storage.isPinned(threadId) } ?: false
            val admins = group.admins.map { it.serialize() to true }.toMap()
            val members = group.members.filterNot { it.serialize() !in admins.keys }.map { it.serialize() to false }.toMap()
            GroupInfo.LegacyGroupInfo(
                sessionId = groupPublicKey,
                name = group.title,
                members = admins + members,
                priority = if (isPinned) ConfigBase.PRIORITY_PINNED else ConfigBase.PRIORITY_VISIBLE,
                encPubKey = (encryptionKeyPair.publicKey as DjbECPublicKey).publicKey,  // 'serialize()' inserts an extra byte
                encSecKey = encryptionKeyPair.privateKey.serialize(),
                disappearingTimer = recipient.expireMessages.toLong(),
                joinedAt = (group.formationTimestamp / 1000L)
            )
        }
        (allOpenGroups + allLgc).forEach { groupInfo ->
            groupConfig.set(groupInfo)
        }
        val dump = groupConfig.dump()
        groupConfig.free()
        if (dump.isEmpty()) return null
        return dump
    }

    @JvmField
    val DELETE_INACTIVE_GROUPS: String = """
        DELETE FROM ${GroupDatabase.TABLE_NAME} WHERE ${GroupDatabase.GROUP_ID} IN (SELECT ${ThreadDatabase.ADDRESS} FROM ${ThreadDatabase.TABLE_NAME} WHERE ${ThreadDatabase.MESSAGE_COUNT} <= 0 AND ${ThreadDatabase.ADDRESS} LIKE '${GroupUtil.CLOSED_GROUP_PREFIX}%');
        DELETE FROM ${ThreadDatabase.TABLE_NAME} WHERE ${ThreadDatabase.ADDRESS} IN (SELECT ${ThreadDatabase.ADDRESS} FROM ${ThreadDatabase.TABLE_NAME} WHERE ${ThreadDatabase.MESSAGE_COUNT} <= 0 AND ${ThreadDatabase.ADDRESS} LIKE '${GroupUtil.CLOSED_GROUP_PREFIX}%');
    """.trimIndent()

    @JvmField
    val DELETE_INACTIVE_ONE_TO_ONES: String = """
        DELETE FROM ${ThreadDatabase.TABLE_NAME} WHERE ${ThreadDatabase.MESSAGE_COUNT} <= 0 AND ${ThreadDatabase.ADDRESS} NOT LIKE '${GroupUtil.CLOSED_GROUP_PREFIX}%' AND ${ThreadDatabase.ADDRESS} NOT LIKE '${GroupUtil.OPEN_GROUP_PREFIX}%' AND ${ThreadDatabase.ADDRESS} NOT LIKE '${GroupUtil.OPEN_GROUP_INBOX_PREFIX}%';
    """.trimIndent()

}