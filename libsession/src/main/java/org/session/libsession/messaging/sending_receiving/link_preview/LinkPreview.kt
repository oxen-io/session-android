package org.session.libsession.messaging.sending_receiving.link_preview

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.session.libsession.messaging.sending_receiving.attachments.Attachment
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentId
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsignal.utilities.JsonUtil
import java.io.IOException

data class LinkPreview(
    @JsonProperty val url: String,
    @JsonProperty val title: String,
    @JsonProperty val attachmentId: AttachmentId?,
    @JsonIgnore var thumbnail: Attachment?
) {
    constructor(url: String, title: String, thumbnail: DatabaseAttachment): this(url, title, thumbnail.attachmentId, thumbnail)
    constructor(url: String, title: String, thumbnail: Attachment?): this(url, title, null, thumbnail)
    constructor(
        @JsonProperty("url") url: String,
        @JsonProperty("title") title: String,
        @JsonProperty("attachmentId") attachmentId: AttachmentId?
    ): this(url, title, attachmentId, null)

    @Throws(IOException::class)
    fun serialize(): String = JsonUtil.toJsonThrows(this)

    companion object {
        @Throws(IOException::class)
        fun deserialize(serialized: String): LinkPreview {
            return JsonUtil.fromJson(serialized, LinkPreview::class.java)
        }
    }
}
