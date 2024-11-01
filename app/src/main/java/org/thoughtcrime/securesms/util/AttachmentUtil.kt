package org.thoughtcrime.securesms.util

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.AttachmentDownloadJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.sending_receiving.attachments.Attachment
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentTransferProgress
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment

private const val ZERO_SIZE = "0.00"
private const val KILO_SIZE = 1024f
private const val MB_SUFFIX = "MB"
private const val KB_SUFFIX = "KB"

fun Attachment.displaySize(): String {

    val kbSize = size / KILO_SIZE
    val needsMb = kbSize > KILO_SIZE
    val sizeText = "%.2f".format(if (needsMb) kbSize / KILO_SIZE else kbSize)
    val displaySize = when {
        sizeText == ZERO_SIZE -> "0.01"
        sizeText.endsWith(".00") -> sizeText.takeWhile { it != '.' }
        else -> sizeText
    }
    return "$displaySize${if (needsMb) MB_SUFFIX else KB_SUFFIX}"
}

fun JobQueue.createAndStartAttachmentDownload(attachment: DatabaseAttachment) {
    val attachmentId = attachment.attachmentId.rowId
    if (attachment.transferState == AttachmentTransferProgress.TRANSFER_PROGRESS_PENDING
        && MessagingModuleConfiguration.shared.storage.getAttachmentUploadJob(attachmentId) == null) {
        // start download
        add(AttachmentDownloadJob(attachmentId, attachment.mmsId))
    }
}