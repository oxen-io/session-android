package org.session.libsession.messaging.sending_receiving.link_preview

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.session.libsession.messaging.sending_receiving.attachments.Attachment
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentId
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.guava.Optional
import java.io.IOException
import java.util.Objects

class LinkPreview {
    @JsonProperty
    val url: String

    @JsonProperty
    val title: String

    @JsonProperty
    val attachmentId: AttachmentId?

    @JsonIgnore
    var thumbnail: Optional<Attachment>

    constructor(url: String, title: String, thumbnail: DatabaseAttachment) {
        this.url = url
        this.title = title
        this.thumbnail = Optional.of(thumbnail)
        this.attachmentId = thumbnail.attachmentId
    }

    constructor(url: String, title: String, thumbnail: Optional<Attachment>) {
        this.url = url
        this.title = title
        this.thumbnail = thumbnail
        this.attachmentId = null
    }

    constructor(
        @JsonProperty("url") url: String,
        @JsonProperty("title") title: String,
        @JsonProperty("attachmentId") attachmentId: AttachmentId?
    ) {
        this.url = url
        this.title = title
        this.attachmentId = attachmentId
        this.thumbnail = Optional.absent()
    }

    @Throws(IOException::class)
    fun serialize(): String {
        return JsonUtil.toJsonThrows(this)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as LinkPreview
        return url == that.url && title == that.title && attachmentId == that.attachmentId && thumbnail == that.thumbnail
    }

    override fun hashCode(): Int {
        return Objects.hash(url, title, attachmentId, thumbnail)
    }

    companion object {
        @Throws(IOException::class)
        fun deserialize(serialized: String): LinkPreview {
            return JsonUtil.fromJson(serialized, LinkPreview::class.java)
        }
    }
}
