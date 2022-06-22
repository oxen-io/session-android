package org.thoughtcrime.securesms.database.model

data class ReactionRecord(
    val id: Long = 0,
    val messageId: Long,
    val author: String,
    val emoji: String,
    val serverId: String = "",
    val dateSent: Long = 0,
    val dateReceived: Long = 0
)
