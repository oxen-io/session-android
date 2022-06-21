package org.thoughtcrime.securesms.database.model

class ReactionRecord(
    val messageId: Long,
    val author: String,
    val emoji: String,
    val serverId: String = "",
    val dateSent: Long = 0,
    val dateReceived: Long = 0
)