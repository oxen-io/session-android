package org.thoughtcrime.securesms.sskenvironment

import android.content.Context
import network.loki.messenger.libsession_util.util.UserPic
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.utilities.SessionId
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.IdPrefix
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities

class ProfileManager(private val context: Context, private val configFactory: ConfigFactory) : SSKEnvironment.ProfileManagerProtocol {

    override fun setNickname(context: Context, recipient: Recipient, nickname: String?) {
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

    override fun setName(context: Context, recipient: Recipient, name: String) {
        // New API
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

    override fun setProfilePictureURL(context: Context, recipient: Recipient, profilePictureURL: String) {
        val job = RetrieveProfileAvatarJob(recipient, profilePictureURL)
        val jobManager = ApplicationContext.getInstance(context).jobManager
        jobManager.add(job)
        val sessionID = recipient.address.serialize()
        val contactDatabase = DatabaseComponent.get(context).sessionContactDatabase()
        var contact = contactDatabase.getContactWithSessionID(sessionID)
        if (contact == null) contact = Contact(sessionID)
        contact.threadID = DatabaseComponent.get(context).storage().getThreadId(recipient.address)
        if (contact.profilePictureURL != profilePictureURL) {
            contact.profilePictureURL = profilePictureURL
            contactDatabase.setContact(contact)
        }
        contactUpdatedInternal(contact)
    }

    override fun setProfileKey(context: Context, recipient: Recipient, profileKey: ByteArray?) {
        // New API
        val sessionID = recipient.address.serialize()
        val contactDatabase = DatabaseComponent.get(context).sessionContactDatabase()
        var contact = contactDatabase.getContactWithSessionID(sessionID)
        if (contact == null) contact = Contact(sessionID)
        contact.threadID = DatabaseComponent.get(context).storage().getThreadId(recipient.address)
        if (!contact.profilePictureEncryptionKey.contentEquals(profileKey)) {
            contact.profilePictureEncryptionKey = profileKey
            contactDatabase.setContact(contact)
        }
        // Old API
        val database = DatabaseComponent.get(context).recipientDatabase()
        database.setProfileKey(recipient, profileKey)
        contactUpdatedInternal(contact)
    }

    override fun setUnidentifiedAccessMode(context: Context, recipient: Recipient, unidentifiedAccessMode: Recipient.UnidentifiedAccessMode) {
        val database = DatabaseComponent.get(context).recipientDatabase()
        database.setUnidentifiedAccessMode(recipient, unidentifiedAccessMode)
    }

    override fun contactUpdatedInternal(contact: Contact) {
        val contactConfig = configFactory.contacts ?: return
        if (contact.sessionID == TextSecurePreferences.getLocalNumber(context)) return
        val sessionId = SessionId(contact.sessionID)
        if (sessionId.prefix != IdPrefix.STANDARD) return // only internally store standard session IDs
        contactConfig.upsertContact(contact.sessionID) {
            this.name = contact.name.orEmpty()
            this.nickname = contact.nickname.orEmpty()
            val url = contact.profilePictureURL
            val key = contact.profilePictureEncryptionKey
            if (!url.isNullOrEmpty() && key != null && key.size == 32) {
                this.profilePicture = UserPic(url, key)
            }
        }
        if (contactConfig.needsPush()) {
            ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(context)
        }
    }

}