package org.thoughtcrime.securesms.database.model

import org.session.libsession.messaging.sending_receiving.reactions.ReactionModel
import org.session.libsession.utilities.Address

class ReactionRecord(val id: Long, val author: Address, emoji: String, react: Boolean, missing: Boolean) {
    val emoji: String
    val isReact: Boolean
    val isMissing: Boolean

    init {
        this.emoji = emoji
        isReact = react
        isMissing = missing
    }

    val reactionModel: ReactionModel
        get() = ReactionModel(id, author, emoji, isReact, isMissing)
}