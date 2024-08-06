/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.mms

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import androidx.annotation.DrawableRes
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.messaging.sending_receiving.attachments.Attachment
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentTransferProgress
import org.session.libsession.messaging.sending_receiving.attachments.UriAttachment
import org.session.libsession.utilities.StringSubstitutionConstants.EMOJI_KEY
import org.session.libsession.utilities.Util.equals
import org.session.libsession.utilities.Util.hashCode
import org.session.libsignal.utilities.Util.SECURE_RANDOM
import org.session.libsignal.utilities.guava.Optional
import org.thoughtcrime.securesms.conversation.v2.Util
import org.thoughtcrime.securesms.util.MediaUtil

abstract class Slide(@JvmField protected val context: Context, protected val attachment: Attachment) {
    val contentType: String
        get() = attachment.contentType

    val uri: Uri?
        get() = attachment.dataUri

    open val thumbnailUri: Uri?
        get() = attachment.thumbnailUri

    val body: Optional<String>
        get() {
            if (MediaUtil.isAudio(attachment)) {
                // A missing file name is the legacy way to determine if an audio attachment is
                // a voice note vs. other arbitrary audio attachments.
                if (attachment.isVoiceNote || attachment.fileName.isNullOrEmpty()) {
                    val baseString = context.getString(R.string.messageVoice)
                    val languageIsLTR = Util.usingLeftToRightLanguage(context)
                    val attachmentString = if (languageIsLTR) {
                        "🎙 $baseString"
                    } else {
                        "$baseString 🎙"
                    }
                    return Optional.fromNullable(attachmentString)
                }
            }
            val txt = Phrase.from(context, R.string.attachmentsNotification)
                .put(EMOJI_KEY, emojiForMimeType())
                .format().toString()
            return Optional.fromNullable(txt)
        }

    private fun emojiForMimeType(): String {
        return if (MediaUtil.isGif(attachment)) {
            "🎡"
        } else if (MediaUtil.isImage(attachment)) {
            "📷"
        } else if (MediaUtil.isVideo(attachment)) {
            "🎥"
        } else if (MediaUtil.isAudio(attachment)) {
            "🎧"
        } else if (MediaUtil.isFile(attachment)) {
            "📎"
        } else {
            // We don't provide emojis for other mime-types such as VCARD
            ""
        }
    }

    val caption: Optional<String?>
        get() = Optional.fromNullable(attachment.caption)

    val fileName: Optional<String?>
        get() = Optional.fromNullable(attachment.fileName)

    val fastPreflightId: String?
        get() = attachment.fastPreflightId

    val fileSize: Long
        get() = attachment.size

    open fun hasImage(): Boolean { return false }

    open fun hasVideo(): Boolean { return false }

    open fun hasAudio(): Boolean { return false }

    open fun hasDocument(): Boolean { return false }

    open val contentDescription: String
        get() = ""

    fun asAttachment(): Attachment { return attachment }

    val isInProgress: Boolean
        get() = attachment.isInProgress

    val isPendingDownload: Boolean
        get() = transferState == AttachmentTransferProgress.TRANSFER_PROGRESS_FAILED ||
                transferState == AttachmentTransferProgress.TRANSFER_PROGRESS_PENDING

    val transferState: Int
        get() = attachment.transferState

    @DrawableRes
    open fun getPlaceholderRes(theme: Resources.Theme?): Int {
        throw AssertionError("getPlaceholderRes() called for non-drawable slide")
    }

    open fun hasPlaceholder(): Boolean { return false }

    open fun hasPlayOverlay(): Boolean { return false }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is Slide) return false

        return (equals(this.contentType, other.contentType) &&
                hasAudio()         == other.hasAudio()      &&
                hasImage()         == other.hasImage()      &&
                hasVideo()         == other.hasVideo())     &&
                this.transferState == other.transferState   &&
                equals(this.uri, other.uri)                 &&
                equals(this.thumbnailUri, other.thumbnailUri)
    }

    override fun hashCode(): Int {
        return hashCode(contentType, hasAudio(), hasImage(), hasVideo(), uri, thumbnailUri, transferState)
    }

    companion object {
        @JvmStatic
        protected fun constructAttachmentFromUri(
            context: Context,
            uri: Uri,
            defaultMime: String,
            size: Long,
            width: Int,
            height: Int,
            hasThumbnail: Boolean,
            fileName: String?,
            caption: String?,
            voiceNote: Boolean,
            quote: Boolean
        ): Attachment {
            val resolvedType =
                Optional.fromNullable(MediaUtil.getMimeType(context, uri)).or(defaultMime)
            val fastPreflightId = SECURE_RANDOM.nextLong().toString()
            return UriAttachment(
                uri,
                if (hasThumbnail) uri else null,
                resolvedType!!,
                AttachmentTransferProgress.TRANSFER_PROGRESS_STARTED,
                size,
                width,
                height,
                fileName,
                fastPreflightId,
                voiceNote,
                quote,
                caption
            )
        }
    }
}