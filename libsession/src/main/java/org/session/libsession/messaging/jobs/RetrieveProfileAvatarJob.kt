package org.session.libsession.messaging.jobs

import org.session.libsession.avatars.AvatarHelper
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.DownloadUtilities.downloadFile
import org.session.libsession.utilities.TextSecurePreferences.Companion.setProfileAvatarId
import org.session.libsession.utilities.TextSecurePreferences.Companion.setProfilePictureURL
import org.session.libsession.utilities.Util.copy
import org.session.libsession.utilities.Util.equals
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.streams.ProfileCipherInputStream
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Util.SECURE_RANDOM
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentSkipListSet

class RetrieveProfileAvatarJob(
    private val profileAvatar: String?, val recipientAddress: Address,
    private val profileKey: ByteArray?
): Job {
    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 3

    companion object {
        val TAG = RetrieveProfileAvatarJob::class.simpleName
        val KEY: String = "RetrieveProfileAvatarJob"

        // Keys used for database storage
        private const val PROFILE_AVATAR_KEY = "profileAvatar"
        private const val RECEIPIENT_ADDRESS_KEY = "recipient"
        private const val PROFILE_KEY = "profileKey"

        val errorUrls = ConcurrentSkipListSet<String>()

    }

    override suspend fun execute(dispatcherName: String) {
        val delegate = delegate ?: return Log.w(TAG, "RetrieveProfileAvatarJob has no delegate method to work with!")
        if (profileAvatar != null && profileAvatar in errorUrls) return delegate.handleJobFailed(this, dispatcherName, Exception("Profile URL 404'd this app instance"))
        val context = MessagingModuleConfiguration.shared.context
        val storage = MessagingModuleConfiguration.shared.storage
        val recipient = Recipient.from(context, recipientAddress, true)

        if (profileKey == null || (profileKey.size != 32 && profileKey.size != 16)) {
            return delegate.handleJobFailedPermanently(this, dispatcherName, Exception("Recipient profile key is gone!"))
        }

        // Commit '78d1e9d' (fix: open group threads and avatar downloads) had this commented out so
        // it's now limited to just the current user case
        if (
                recipient.isLocalNumber &&
                AvatarHelper.avatarFileExists(context, recipient.resolve().address) &&
                equals(profileAvatar, recipient.resolve().profileAvatar)
        ) {
            Log.w(TAG, "Already retrieved profile avatar: $profileAvatar")
            return
        }

        if (profileAvatar.isNullOrEmpty()) {
            Log.w(TAG, "Removing profile avatar for: " + recipient.address.serialize())

            if (recipient.isLocalNumber) {
                setProfileAvatarId(context, SECURE_RANDOM.nextInt())
                setProfilePictureURL(context, null)
            }

            AvatarHelper.delete(context, recipient.address)
            storage.setProfilePicture(recipient, null, null)
            return
        }

        val downloadDestination = File.createTempFile("avatar", ".jpg", context.cacheDir)

        try {
            downloadFile(downloadDestination, profileAvatar)
            val avatarStream: InputStream = ProfileCipherInputStream(FileInputStream(downloadDestination), profileKey)
            val decryptDestination = File.createTempFile("avatar", ".jpg", context.cacheDir)
            copy(avatarStream, FileOutputStream(decryptDestination))
            decryptDestination.renameTo(AvatarHelper.getAvatarFile(context, recipient.address))

            if (recipient.isLocalNumber) {
                setProfileAvatarId(context, SECURE_RANDOM.nextInt())
                setProfilePictureURL(context, profileAvatar)
            }

            storage.setProfilePicture(recipient, profileAvatar, profileKey)
        } catch (e: Exception) {
            Log.e("Loki", "Failed to download profile avatar", e)
            if (failureCount + 1 >= maxFailureCount) {
                errorUrls += profileAvatar
            }
            return delegate.handleJobFailed(this, dispatcherName, e)
        } finally {
            downloadDestination.delete()
        }
        return delegate.handleJobSucceeded(this, dispatcherName)
    }

    override fun serialize(): Data {
        val data = Data.Builder()
            .putString(PROFILE_AVATAR_KEY, profileAvatar)
            .putString(RECEIPIENT_ADDRESS_KEY, recipientAddress.serialize())

        if (profileKey != null) {
            data.putByteArray(PROFILE_KEY, profileKey)
        }

        return data.build()
    }

    override fun getFactoryKey(): String {
        return KEY
    }

    class Factory: Job.Factory<RetrieveProfileAvatarJob> {
        override fun create(data: Data): RetrieveProfileAvatarJob {
            val profileAvatar = if (data.hasString(PROFILE_AVATAR_KEY)) { data.getString(PROFILE_AVATAR_KEY) } else { null }
            val recipientAddress = Address.fromSerialized(data.getString(RECEIPIENT_ADDRESS_KEY))
            val profileKey = data.getByteArray(PROFILE_KEY)
            return RetrieveProfileAvatarJob(profileAvatar, recipientAddress, profileKey)
        }
    }
}