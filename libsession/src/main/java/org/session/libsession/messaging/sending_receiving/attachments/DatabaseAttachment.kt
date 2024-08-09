package org.session.libsession.messaging.sending_receiving.attachments

import android.net.Uri
import org.session.libsession.messaging.MessagingModuleConfiguration.Companion.shared

class DatabaseAttachment(
  @JvmField val attachmentId: AttachmentId, @JvmField val mmsId: Long,
  val hasData: Boolean, val hasThumbnail: Boolean,
  contentType: String?, transferProgress: Int, size: Long,
  fileName: String?, location: String?, key: String?, relay: String?,
  digest: ByteArray?, fastPreflightId: String?, voiceNote: Boolean,
  width: Int, height: Int, quote: Boolean, caption: String?,
  url: String?
) : Attachment(
    contentType!!,
    transferProgress,
    size,
    fileName,
    location,
    key,
    relay,
    digest,
    fastPreflightId,
    voiceNote,
    width,
    height,
    quote,
    caption,
    url
) {
    override fun getDataUri(): Uri? = attachmentId.takeIf { hasData }?.let(shared.storage::getAttachmentDataUri)
    override fun getThumbnailUri(): Uri? = attachmentId.takeIf { hasThumbnail }?.let(shared.storage::getAttachmentThumbnailUri)
    override fun equals(other: Any?): Boolean = other != null && other is DatabaseAttachment && other.attachmentId == attachmentId
    override fun hashCode(): Int = attachmentId.hashCode()
}
