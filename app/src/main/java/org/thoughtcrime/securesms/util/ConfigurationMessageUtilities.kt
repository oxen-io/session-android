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
import org.session.libsession.messaging.utilities.SessionId
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.dependencies.DatabaseComponent

object ConfigurationMessageUtilities {

    @JvmStatic
    fun syncConfigurationIfNeeded(context: Context) {
        // add if check here to schedule new config job process and return early
        val userPublicKey = TextSecurePreferences.getLocalNumber(context) ?: return
        val storage = MessagingModuleConfiguration.shared.storage
        if (ConfigBase.isNewConfigEnabled) {
            // don't schedule job if we already have one
            val ourDestination = Destination.Contact(userPublicKey)
            if (storage.getConfigSyncJob(ourDestination) != null) {
                Log.d("Loki", "ConfigSyncJob is already running for our destination")
                return
            }
            val newConfigSync = ConfigurationSyncJob(ourDestination)
            Log.d("Loki", "Scheduling new ConfigurationSyncJob")
            JobQueue.shared.add(newConfigSync)
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
        val storage = MessagingModuleConfiguration.shared.storage
        if (ConfigBase.isNewConfigEnabled) {
            // schedule job if none exist
            // don't schedule job if we already have one
            Log.d("Loki-DBG", "Forcing config sync")
            val ourDestination = Destination.Contact(userPublicKey)
            val currentStorageJob = storage.getConfigSyncJob(ourDestination)
            if (currentStorageJob != null) {
                (currentStorageJob as ConfigurationSyncJob).shouldRunAgain.set(true)
                Log.d("Loki-DBG", "Not scheduling another one")
                return Promise.ofFail(NullPointerException("A job is already pending or in progress, don't schedule another job"))
            }
            val newConfigSync = ConfigurationSyncJob(ourDestination)
            Log.d("Loki", "Scheduling new ConfigurationSyncJob")
            JobQueue.shared.add(newConfigSync)
            // treat this promise as succeeding now (so app continues running and doesn't block UI)
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
        val promise = MessageSender.send(configurationMessage, Destination.from(Address.fromSerialized(userPublicKey)))
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
        profile.setNtsHidden(ownThreadId != null)
        if (ownThreadId != null) {
            // have NTS thread
            val ntsPinned = storage.isPinned(ownThreadId)
            profile.setNtsPriority(if (ntsPinned) 1 else 0) // TODO: implement the pinning priority here in future
        }
        val dump = profile.dump()
        profile.free()
        return dump
    }

    fun generateContactConfigDump(): ByteArray? {
        val secretKey = maybeUserSecretKey() ?: return null
        val storage = MessagingModuleConfiguration.shared.storage
        val localUserKey = storage.getUserPublicKey() ?: return null
        val contactsWithSettings = storage.getAllContacts().filter { recipient ->
            recipient.sessionID != localUserKey
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
                        val sessionId = SessionId(recipient.address.serialize())
                        if (recipient.isLocalNumber) null // this is handled by the user profile NTS data
                        else convoConfig.getOrConstructOneToOne(recipient.address.serialize())
                    }
                    else -> null
                }
                if (contact == null) {
                    current = reader.next
                    continue
                }
                contact.lastRead = current.lastSeen
                contact.unread = false // TODO: make the forced unread work at DB level
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

        val allLgc = storage.getAllGroups().filter { it.isClosedGroup && it.isActive }.mapNotNull { group ->
            val groupAddress = Address.fromSerialized(group.encodedId)
            val groupPublicKey = GroupUtil.doubleDecodeGroupID(groupAddress.serialize()).toHexString()
            val recipient = storage.getRecipientSettings(groupAddress) ?: return@mapNotNull null
            val encryptionKeyPair = storage.getLatestClosedGroupEncryptionKeyPair(groupPublicKey) ?: return@mapNotNull null
            val threadId = storage.getThreadId(group.encodedId)
            val isPinned = threadId?.let { storage.isPinned(threadId) } ?: false
            val sessionId = GroupUtil.doubleEncodeGroupID(group.getId())
            val admins = group.admins.map { it.serialize() to true }.toMap()
            val members = group.members.filterNot { it.serialize() !in admins.keys }.map { it.serialize() to false }.toMap()
            GroupInfo.LegacyGroupInfo(
                sessionId = sessionId,
                name = group.title,
                members = admins + members,
                hidden = threadId == null,
                priority = if (isPinned) 1 else 0,
                encPubKey = encryptionKeyPair.publicKey.serialize(),
                encSecKey = encryptionKeyPair.privateKey.serialize(),
                disappearingTimer = recipient.expireMessages.toLong()
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

}