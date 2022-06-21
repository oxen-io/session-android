package org.session.libsession.messaging.sending_receiving.reactions

class ReactionModel(
    val timestamp: Long,
    val author: String,
    val emoji: String,
    val serverId: String = "",
    val sentTimestamp: Long = 0,
    val receivedTimestamp: Long = 0,
)
