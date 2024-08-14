package org.thoughtcrime.securesms.database.model

import org.thoughtcrime.securesms.util.MediaUtil

data class MmsAttachmentInfo(val dataFile: String?, val thumbnailFile: String?, val contentType: String?)
fun MmsAttachmentInfo.isImage() = MediaUtil.isImageType(contentType)
fun MmsAttachmentInfo.isThumbnailNonNull() = thumbnailFile?.isNotEmpty() == true
