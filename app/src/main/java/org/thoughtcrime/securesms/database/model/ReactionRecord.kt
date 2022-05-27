package org.thoughtcrime.securesms.database.model

/**
 * Represents an individual reaction to a message.
 */
data class ReactionRecord(
  val emoji: String,
  val author: String,
  val dateSent: Long,
  val dateReceived: Long
)
