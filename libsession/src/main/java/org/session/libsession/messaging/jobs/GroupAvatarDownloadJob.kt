package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.GroupUtil

class GroupAvatarDownloadJob(val room: String, val server: String) : Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 10

    override fun execute(dispatcherName: String) {
        val storage = MessagingModuleConfiguration.shared.storage
        val imageId = storage.getOpenGroup(room, server)?.imageId ?: return
        try {
            val bytes = OpenGroupApi.downloadOpenGroupProfilePicture(server, room, imageId).get()
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
            .build()
    }

    override fun getFactoryKey(): String = KEY

    companion object {
        const val KEY = "GroupAvatarDownloadJob"

        private const val ROOM = "room"
        private const val SERVER = "server"
    }

    class Factory : Job.Factory<GroupAvatarDownloadJob> {

        override fun create(data: Data): GroupAvatarDownloadJob {
            return GroupAvatarDownloadJob(
                data.getString(ROOM),
                data.getString(SERVER)
            )
        }
    }
}