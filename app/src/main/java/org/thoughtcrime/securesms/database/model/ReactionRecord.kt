package org.thoughtcrime.securesms.database.model

import org.session.libsession.messaging.sending_receiving.reactions.ReactionModel

class ReactionRecord(
    val id: Long,
    val author: String,
    val emoji: String,
    val dateSent: Long,
    val dateReceived: Long
) {

    val reactionModel: ReactionModel
        get() = ReactionModel(id, author, emoji, true)

}