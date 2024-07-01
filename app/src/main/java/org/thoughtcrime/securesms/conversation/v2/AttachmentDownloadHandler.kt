package org.thoughtcrime.securesms.conversation.v2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.jobs.AttachmentDownloadJob
import org.session.libsession.messaging.jobs.AttachmentUploadJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentTransferProgress
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.util.flatten
import org.thoughtcrime.securesms.util.timedBuffer

/**
 * [AttachmentDownloadHandler] is responsible for handling attachment download requests. These
 * requests will go through different level of checking before they are queued for download.
 *
 * To use this handler, call [onAttachmentDownloadRequest] with the attachment that needs to be
 * downloaded. The call to [onAttachmentDownloadRequest] is cheap and can be called multiple times.
 */
class AttachmentDownloadHandler(
    private val storage: StorageProtocol,
    private val messageDataProvider: MessageDataProvider,
    jobQueue: JobQueue = JobQueue.shared,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default) + SupervisorJob(),
) {
    companion object {
        private const val BUFFER_TIMEOUT_MILLS = 500L
        private const val BUFFER_MAX_ITEMS = 10
        private const val LOG_TAG = "AttachmentDownloadHelper"
    }

    private val downloadRequests = Channel<DatabaseAttachment>(UNLIMITED)

    init {
        scope.launch(Dispatchers.Default) {
            downloadRequests
                .receiveAsFlow()
                .timedBuffer(BUFFER_TIMEOUT_MILLS, BUFFER_MAX_ITEMS)
                .map(::filterEligibleAttachments)
                .flatten()
                .collect { attachment ->
                    jobQueue.add(
                        AttachmentDownloadJob(
                            attachmentID = attachment.attachmentId.rowId,
                            databaseMessageID = attachment.mmsId
                        )
                    )
                }
        }
    }

    /**
     * Filter attachments that are eligible for creating download jobs.
     *
     */
    private fun filterEligibleAttachments(attachments: List<DatabaseAttachment>): List<DatabaseAttachment> {
        val pendingAttachmentIDs = storage
            .getAllPendingJobs(AttachmentDownloadJob.KEY, AttachmentUploadJob.KEY)
            .values
            .mapNotNull {
                (it as? AttachmentUploadJob)?.attachmentID
                    ?: (it as? AttachmentDownloadJob)?.attachmentID
            }
            .toSet()


        return attachments.filter { attachment ->
            eligibleForDownloadTask(
                attachment,
                pendingAttachmentIDs,
            )
        }
    }

    /**
     * Check if the attachment is eligible for download task.
     */
    private fun eligibleForDownloadTask(
        attachment: DatabaseAttachment,
        pendingJobsAttachmentRowIDs: Set<Long>,
    ): Boolean {
        if (attachment.attachmentId.rowId in pendingJobsAttachmentRowIDs) {
            return false
        }

        val threadID = storage.getThreadIdForMms(attachment.mmsId)

        return AttachmentDownloadJob.eligibleForDownload(
            threadID, storage, messageDataProvider, attachment.mmsId,
        )
    }


    fun onAttachmentDownloadRequest(attachment: DatabaseAttachment) {
        if (attachment.transferState != AttachmentTransferProgress.TRANSFER_PROGRESS_PENDING) {
            Log.i(
                LOG_TAG,
                "Attachment ${attachment.attachmentId} is not pending, skipping download"
            )
            return
        }

        downloadRequests.trySend(attachment)
    }
}
