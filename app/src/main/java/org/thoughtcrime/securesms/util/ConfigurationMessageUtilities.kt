package org.thoughtcrime.securesms.util

import android.content.Context
import nl.komponents.kovenant.Promise

object ConfigurationMessageUtilities {

    @JvmStatic
    fun syncConfigurationIfNeeded(context: Context) {
        return
//        val userPublicKey = TextSecurePreferences.getLocalNumber(context) ?: return
//        val lastSyncTime = TextSecurePreferences.getLastConfigurationSyncTime(context)
//        val now = System.currentTimeMillis()
//        if (now - lastSyncTime < 7 * 24 * 60 * 60 * 1000) return
//        val contacts = ContactUtilities.getAllContacts(context).filter { recipient ->
//            !recipient.name.isNullOrEmpty() && !recipient.isLocalNumber && recipient.address.serialize().isNotEmpty()
//        }.map { recipient ->
//            ConfigurationMessage.Contact(
//                publicKey = recipient.address.serialize(),
//                name = recipient.name!!,
//                profilePicture = recipient.profileAvatar,
//                profileKey = recipient.profileKey,
//                isApproved = recipient.isApproved,
//                isBlocked = recipient.isBlocked,
//                didApproveMe = recipient.hasApprovedMe()
//            )
//        }
//        val configurationMessage = ConfigurationMessage.getCurrent(contacts) ?: return
//        MessageSender.send(configurationMessage, Address.fromSerialized(userPublicKey))
//        TextSecurePreferences.setLastConfigurationSyncTime(context, now)
    }

    fun forceSyncConfigurationNowIfNeeded(context: Context): Promise<Unit, Exception> {
        return Promise.ofSuccess(Unit)
//        val userPublicKey = TextSecurePreferences.getLocalNumber(context) ?: return Promise.ofSuccess(Unit)
//        val contacts = ContactUtilities.getAllContacts(context).filter { recipient ->
//            !recipient.isGroupRecipient && !recipient.name.isNullOrEmpty() && !recipient.isLocalNumber && recipient.address.serialize().isNotEmpty()
//        }.map { recipient ->
//            ConfigurationMessage.Contact(
//                publicKey = recipient.address.serialize(),
//                name = recipient.name!!,
//                profilePicture = recipient.profileAvatar,
//                profileKey = recipient.profileKey,
//                isApproved = recipient.isApproved,
//                isBlocked = recipient.isBlocked,
//                didApproveMe = recipient.hasApprovedMe()
//            )
//        }
//        val configurationMessage = ConfigurationMessage.getCurrent(contacts) ?: return Promise.ofSuccess(Unit)
//        val promise = MessageSender.send(configurationMessage, Destination.from(Address.fromSerialized(userPublicKey)))
//        TextSecurePreferences.setLastConfigurationSyncTime(context, System.currentTimeMillis())
//        return promise
    }

}