package org.session.libsession.snode.model

import android.util.Base64
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.util.StdConverter

data class StoreMessageResponse @JsonCreator constructor(
    @JsonProperty("hash") val hash: String,
    @JsonProperty("t") val timestamp: Long,
)

class RetrieveMessageResponse @JsonCreator constructor(
    @JsonProperty("messages")
    // Apply converter to the element so that if one of the message fails to deserialize, it will
    // be a null value instead of failing the whole list.
    @JsonDeserialize(contentConverter = RetrieveMessageConverter::class)
    val messages: List<Message?>,
) {
    class Message(
        val hash: String,
        val timestamp: Long?,
        val data: ByteArray,
    )
}

internal class RetrieveMessageConverter : StdConverter<JsonNode, RetrieveMessageResponse.Message?>() {
    override fun convert(value: JsonNode?): RetrieveMessageResponse.Message? {
        value ?: return null

        val hash = value.get("hash")?.asText()?.takeIf { it.isNotEmpty() } ?: return null
        val timestamp = value.get("t")?.asLong()?.takeIf { it > 0 }
        val data = runCatching {
            Base64.decode(value.get("data")?.asText().orEmpty(), Base64.DEFAULT)
        }.getOrNull() ?: return null

        return RetrieveMessageResponse.Message(hash, timestamp, data)
    }
}