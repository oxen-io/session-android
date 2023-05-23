package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.GroupUtil

class GroupAvatarDownloadJob(val server: String, val room: String, val imageId: String?) : Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 10

    override suspend fun execute(dispatcherName: String) {
        if (imageId == null) {
            delegate?.handleJobFailedPermanently(this, dispatcherName, Exception("GroupAvatarDownloadJob now requires imageId"))
            return
        }
        val storage = MessagingModuleConfiguration.shared.storage
        val openGroup = storage.getOpenGroup(room, server)
        if (openGroup == null || storage.getThreadId(openGroup) == null) {
            delegate?.handleJobFailedPermanently(this, dispatcherName, Exception("GroupAvatarDownloadJob openGroup is null"))
            return
        }
        val storedImageId = openGroup.imageId

        if (storedImageId == null || storedImageId != imageId) {
            delegate?.handleJobFailedPermanently(this, dispatcherName, Exception("GroupAvatarDownloadJob imageId does not match the OpenGroup"))
            return
        }

        try {
            val bytes = OpenGroupApi.downloadOpenGroupProfilePicture(server, room, imageId).get()

            // Once the download is complete the imageId might no longer match, so we need to fetch it again just in case
            val postDownloadStoredImageId = storage.getOpenGroup(room, server)?.imageId

            if (postDownloadStoredImageId == null || postDownloadStoredImageId != imageId) {
                delegate?.handleJobFailedPermanently(this, dispatcherName, Exception("GroupAvatarDownloadJob imageId no longer matches the OpenGroup"))
                return
            }

            val groupId = GroupUtil.getEncodedOpenGroupID("$server.$room".toByteArray())
            storage.updateProfilePicture(groupId, bytes)
            storage.updateTimestampUpdated(groupId, SnodeAPI.nowWithOffset)
            delegate?.handleJobSucceeded(this, dispatcherName)
        } catch (e: Exception) {
            delegate?.handleJobFailed(this, dispatcherName, e)
        }
    }

    override fun serialize(): Data {
        return Data.Builder()
            .putString(ROOM, room)
            .putString(SERVER, server)
            .putString(IMAGE_ID, imageId)
            .build()
    }

    override fun getFactoryKey(): String = KEY

    companion object {
        const val KEY = "GroupAvatarDownloadJob"

        private const val ROOM = "room"
        private const val SERVER = "server"
        private const val IMAGE_ID = "imageId"
    }

    class Factory : Job.Factory<GroupAvatarDownloadJob> {

        override fun create(data: Data): GroupAvatarDownloadJob {
            return GroupAvatarDownloadJob(
                data.getString(SERVER),
                data.getString(ROOM),
                if (data.hasString(IMAGE_ID)) { data.getString(IMAGE_ID) } else { null }
            )
        }
    }
}