package org.session.libsession.messaging.messages

data class MarkAsDeletedMessage(
    val messageId: Long,
    val isOutgoing: Boolean
)
