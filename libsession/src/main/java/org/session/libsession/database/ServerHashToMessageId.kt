package org.session.libsession.database

data class ServerHashToMessageId(
    val serverHash: String,
    val sender: String,
    val messageId: Long,
    val isSms: Boolean,
)