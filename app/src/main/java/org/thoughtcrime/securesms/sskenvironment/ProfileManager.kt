package org.thoughtcrime.securesms.sskenvironment

import android.content.Context
import dagger.Lazy
import network.loki.messenger.libsession_util.util.UserPic
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.RetrieveProfileAvatarJob
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.IdPrefix
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.SessionContactDatabase
import org.thoughtcrime.securesms.database.SessionJobDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileManager @Inject constructor(
    private val configFactory: ConfigFactoryProtocol,
    private val storage: Lazy<StorageProtocol>,
    private val contactDatabase: SessionContactDatabase,
    private val recipientDatabase: RecipientDatabase,
    private val jobDatabase: SessionJobDatabase,
    private val preferences: TextSecurePreferences,
) : SSKEnvironment.ProfileManagerProtocol {

    override fun setNickname(context: Context, recipient: Recipient, nickname: String?) {
        if (recipient.isLocalNumber) return
        val accountID = recipient.address.serialize()
        var contact = contactDatabase.getContactWithAccountID(accountID)
        if (contact == null) contact = Contact(accountID)
        contact.threadID = storage.get().getThreadId(recipient.address)
        if (contact.nickname != nickname) {
            contact.nickname = nickname
            contactDatabase.setContact(contact)
        }
        contactUpdatedInternal(contact)
    }

    override fun setName(context: Context, recipient: Recipient, name: String?) {
        // New API
        if (recipient.isLocalNumber) return
        val accountID = recipient.address.serialize()
        var contact = contactDatabase.getContactWithAccountID(accountID)
        if (contact == null) contact = Contact(accountID)
        contact.threadID = storage.get().getThreadId(recipient.address)
        if (contact.name != name) {
            contact.name = name
            contactDatabase.setContact(contact)
        }
        // Old API
        recipientDatabase.setProfileName(recipient, name)
        recipient.notifyListeners()
        contactUpdatedInternal(contact)
    }

    override fun setProfilePicture(
        context: Context,
        recipient: Recipient,
        profilePictureURL: String?,
        profileKey: ByteArray?
    ) {
        val hasPendingDownload = jobDatabase
            .getAllJobs(RetrieveProfileAvatarJob.KEY).any {
                (it.value as? RetrieveProfileAvatarJob)?.recipientAddress == recipient.address
            }

        recipient.resolve()

        val accountID = recipient.address.serialize()
        var contact = contactDatabase.getContactWithAccountID(accountID)
        if (contact == null) contact = Contact(accountID)
        contact.threadID = storage.get().getThreadId(recipient.address)
        if (!contact.profilePictureEncryptionKey.contentEquals(profileKey) || contact.profilePictureURL != profilePictureURL) {
            contact.profilePictureEncryptionKey = profileKey
            contact.profilePictureURL = profilePictureURL
            contactDatabase.setContact(contact)
        }
        contactUpdatedInternal(contact)
        if (!hasPendingDownload) {
            val job = RetrieveProfileAvatarJob(profilePictureURL, recipient.address, profileKey)
            JobQueue.shared.add(job)
        }
    }

    override fun setUnidentifiedAccessMode(context: Context, recipient: Recipient, unidentifiedAccessMode: Recipient.UnidentifiedAccessMode) {
        recipientDatabase.setUnidentifiedAccessMode(recipient, unidentifiedAccessMode)
    }

    override fun contactUpdatedInternal(contact: Contact): String? {
        if (contact.accountID == preferences.getLocalNumber()) return null
        val accountId = AccountId(contact.accountID)
        if (accountId.prefix != IdPrefix.STANDARD) return null // only internally store standard account IDs
        return configFactory.withMutableUserConfigs {
            val contactConfig = it.contacts
            contactConfig.upsertContact(contact.accountID) {
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
            contactConfig.get(contact.accountID)?.hashCode()?.toString()
        }
    }

}