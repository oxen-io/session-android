package org.thoughtcrime.securesms.sskenvironment

import android.content.Context
import network.loki.messenger.libsession_util.util.UserPic
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.RetrieveProfileAvatarJob
import org.session.libsession.messaging.utilities.SessionId
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.IdPrefix
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities

class ProfileManager(private val context: Context, private val configFactory: ConfigFactory) : SSKEnvironment.ProfileManagerProtocol {

    override fun setNickname(context: Context, recipient: Recipient, nickname: String?) {
        if (recipient.isLocalNumber) return
        val sessionID = recipient.address.serialize()
        val contactDatabase = DatabaseComponent.get(context).sessionContactDatabase()
        var contact = contactDatabase.getContactWithSessionID(sessionID)
        if (contact == null) contact = Contact(sessionID)
        contact.threadID = DatabaseComponent.get(context).storage().getThreadId(recipient.address)
        if (contact.nickname != nickname) {
            contact.nickname = nickname
            contactDatabase.setContact(contact)
        }
        contactUpdatedInternal(contact)
    }

    override fun setName(context: Context, recipient: Recipient, name: String?) {
        // New API
        if (recipient.isLocalNumber) return
        val sessionID = recipient.address.serialize()
        val contactDatabase = DatabaseComponent.get(context).sessionContactDatabase()
        var contact = contactDatabase.getContactWithSessionID(sessionID)
        if (contact == null) contact = Contact(sessionID)
        contact.threadID = DatabaseComponent.get(context).storage().getThreadId(recipient.address)
        if (contact.name != name) {
            contact.name = name
            contactDatabase.setContact(contact)
        }
        // Old API
        val database = DatabaseComponent.get(context).recipientDatabase()
        database.setProfileName(recipient, name)
        recipient.notifyListeners()
        contactUpdatedInternal(contact)
    }

    override fun setProfilePicture(
        context: Context,
        recipient: Recipient,
        profilePictureURL: String?,
        profileKey: ByteArray?
    ) {
        val hasPendingDownload = DatabaseComponent
            .get(context)
            .sessionJobDatabase()
            .getAllJobs(RetrieveProfileAvatarJob.KEY).any {
                (it.value as? RetrieveProfileAvatarJob)?.recipientAddress == recipient.address
            }
        val resolved = recipient.resolve()
        DatabaseComponent.get(context).storage().setProfilePicture(
            recipient = resolved,
            newProfileKey = profileKey,
            newProfilePicture = profilePictureURL
        )
        val sessionID = recipient.address.serialize()
        val contactDatabase = DatabaseComponent.get(context).sessionContactDatabase()
        var contact = contactDatabase.getContactWithSessionID(sessionID)
        if (contact == null) contact = Contact(sessionID)
        contact.threadID = DatabaseComponent.get(context).storage().getThreadId(recipient.address)
        if (!contact.profilePictureEncryptionKey.contentEquals(profileKey) || contact.profilePictureURL != profilePictureURL) {
            contact.profilePictureEncryptionKey = profileKey
            contact.profilePictureURL = profilePictureURL
            contactDatabase.setContact(contact)
        }
        contactUpdatedInternal(contact)
        if (!hasPendingDownload) {
            val job = RetrieveProfileAvatarJob(profilePictureURL, recipient.address)
            JobQueue.shared.add(job)
        }
    }

    override fun setUnidentifiedAccessMode(context: Context, recipient: Recipient, unidentifiedAccessMode: Recipient.UnidentifiedAccessMode) {
        val database = DatabaseComponent.get(context).recipientDatabase()
        database.setUnidentifiedAccessMode(recipient, unidentifiedAccessMode)
    }

    override fun contactUpdatedInternal(contact: Contact): String? {
        val contactConfig = configFactory.contacts ?: return null
        if (contact.sessionID == TextSecurePreferences.getLocalNumber(context)) return null
        val sessionId = SessionId(contact.sessionID)
        if (sessionId.prefix != IdPrefix.STANDARD) return null // only internally store standard session IDs
        contactConfig.upsertContact(contact.sessionID) {
            this.name = contact.name.orEmpty()
            this.nickname = contact.nickname.orEmpty()
            val url = contact.profilePictureURL
            val key = contact.profilePictureEncryptionKey
            if (!url.isNullOrEmpty() && key != null && key.size == 32) {
                this.profilePicture = UserPic(url, key)
            } else if (url.isNullOrEmpty() && key == null) {
                this.profilePicture = UserPic.DEFAULT
            }
        }
        if (contactConfig.needsPush()) {
            ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(context)
        }
        return contactConfig.get(contact.sessionID)?.hashCode()?.toString()
    }

}