package org.thoughtcrime.securesms.util

import android.content.Context
import network.loki.messenger.libsession_util.ConfigBase
import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.UserProfile
import network.loki.messenger.libsession_util.util.Contact
import network.loki.messenger.libsession_util.util.UserPic
import nl.komponents.kovenant.Promise
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.ConfigurationSyncJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.control.ConfigurationMessage
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log

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
            val ourDestination = Destination.Contact(userPublicKey)
            if (storage.getConfigSyncJob(ourDestination) != null) return Promise.ofFail(NullPointerException("A job is already pending or in progress, don't schedule another job"))
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
        val config = ConfigurationMessage.getCurrent(listOf()) ?: return null
        val secretKey = maybeUserSecretKey() ?: return null
        val profile = UserProfile.newInstance(secretKey)
        profile.setName(config.displayName)
        val picUrl = config.profilePicture
        val picKey = config.profileKey
        if (!picUrl.isNullOrEmpty() && picKey.isNotEmpty()) {
            profile.setPic(UserPic(picUrl, picKey))
        }
        val dump = profile.dump()
        profile.free()
        return dump
    }

    fun generateContactConfigDump(context: Context): ByteArray? {
        val secretKey = maybeUserSecretKey() ?: return null
        val storage = MessagingModuleConfiguration.shared.storage
        val localUserKey = storage.getUserPublicKey() ?: return null
        val contactsWithSettings = storage.getAllContacts().filter { recipient ->
            recipient.sessionID != localUserKey
        }.map { contact ->
            contact to storage.getRecipientSettings(Address.fromSerialized(contact.sessionID))!!
        }
        val contactConfig = Contacts.newInstance(secretKey)
        for ((contact, settings) in contactsWithSettings) {
            val url = contact.profilePictureURL
            val key = contact.profilePictureEncryptionKey
            val userPic = if (url.isNullOrEmpty() || key?.isNotEmpty() != true) {
                null
            } else {
                UserPic(url, key)
            }
            val contactInfo = Contact(
                id = contact.sessionID,
                name = contact.name,
                nickname = contact.nickname,
                blocked = settings.isBlocked,
                approved = settings.isApproved,
                approvedMe = settings.hasApprovedMe(),
                profilePicture = userPic
            )
            contactConfig.set(contactInfo)
        }
        val dump = contactConfig.dump()
        contactConfig.free()
        return dump
    }

    fun generateConversationVolatileDump(context: Context): ByteArray? {
        TODO()
    }

}