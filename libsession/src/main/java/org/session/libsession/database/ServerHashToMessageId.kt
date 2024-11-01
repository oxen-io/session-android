package org.session.libsession.database

data class ServerHashToMessageId(
    val serverHash: String,
    /**
     * This will only be the "sender" when the message is incoming, when the message is outgoing,
     * the value here could be the receiver of the message, it's better not to rely on opposite
     * meaning of this field.
     */
    val sender: String,
    val messageId: Long,
    val isSms: Boolean,
    val isOutgoing: Boolean,
)