package org.session.libsession.messaging.sending_receiving.reactions

class ReactionModel(
    val id: Long,
    val author: String,
    val emoji: String,
    val react: Boolean
)
