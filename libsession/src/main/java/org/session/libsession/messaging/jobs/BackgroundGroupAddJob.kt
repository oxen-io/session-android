package org.session.libsession.messaging.jobs

import okhttp3.HttpUrl
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.OpenGroupUrlParser
import org.session.libsignal.utilities.Log

class BackgroundGroupAddJob(val joinUrl: String): Job {

    companion object {
        const val KEY = "BackgroundGroupAddJob"

        private const val JOIN_URL = "joinUri"
    }

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 1

    val openGroupId: String? get() {
        val url = HttpUrl.parse(joinUrl) ?: return null
        val server = OpenGroup.getServer(joinUrl)?.toString()?.removeSuffix("/") ?: return null
        val room = url.pathSegments().firstOrNull() ?: return null
        return "$server.$room"
    }

    override fun execute(dispatcherName: String) {
        try {
            val openGroup = OpenGroupUrlParser.parseUrl(joinUrl)
            val storage = MessagingModuleConfiguration.shared.storage
            val allOpenGroups = storage.getAllOpenGroups().map { it.value.joinURL }
            if (allOpenGroups.contains(openGroup.joinUrl())) {
                Log.e("OpenGroupDispatcher", "Failed to add group because", DuplicateGroupException())
                delegate?.handleJobFailed(this, dispatcherName, DuplicateGroupException())
                return
            }
            storage.addOpenGroup(openGroup.joinUrl())
            Log.d(KEY, "onOpenGroupAdded(${openGroup.server})")
            storage.onOpenGroupAdded(openGroup.server)
        } catch (e: Exception) {
            Log.e("OpenGroupDispatcher", "Failed to add group because",e)
            delegate?.handleJobFailed(this, dispatcherName, e)
            return
        }
        Log.d("Loki", "Group added successfully")
        delegate?.handleJobSucceeded(this, dispatcherName)
    }

    override fun serialize(): Data = Data.Builder()
        .putString(JOIN_URL, joinUrl)
        .build()

    override fun getFactoryKey(): String = KEY

    class DuplicateGroupException: Exception("Current open groups already contains this group")

    class Factory : Job.Factory<BackgroundGroupAddJob> {
        override fun create(data: Data): BackgroundGroupAddJob {
            return BackgroundGroupAddJob(
                data.getString(JOIN_URL)
            )
        }
    }

}