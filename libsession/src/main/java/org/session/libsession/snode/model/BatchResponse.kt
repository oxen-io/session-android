package org.session.libsession.snode.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode

data class BatchResponse @JsonCreator constructor(
    @param:JsonProperty("results") val results: List<Item>,
) {
    data class Item @JsonCreator constructor(
        @param:JsonProperty("code") val code: Int,
        @param:JsonProperty("body") val body: JsonNode,
    ) {
        val isSuccessful: Boolean
            get() = code in 200..299
    }
}
