package org.session.libsession.messaging.messages.visible

import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.protos.SignalServiceProtos.DataMessage.Reaction.Action
import org.session.libsignal.utilities.Log
import org.session.libsession.messaging.sending_receiving.reactions.ReactionModel as SignalReaction

class Reaction() {
    var timestamp: Long? = 0
    var publicKey: String? = null
    var emoji: String? = null
    var react: Boolean? = true

    fun isValid(): Boolean {
        return (timestamp != null && publicKey != null)
    }

    companion object {
        const val TAG = "Quote"

        fun fromProto(proto: SignalServiceProtos.DataMessage.Reaction): Reaction? {
            val react = proto.action == Action.REACT
            return Reaction(proto.id, proto.author, proto.emoji, react)
        }

        fun from(signalReaction: SignalReaction?): Reaction? {
            if (signalReaction == null) { return null }
            return Reaction(signalReaction.id, signalReaction.author, signalReaction.emoji, signalReaction.react)
        }
    }

    internal constructor(timestamp: Long, publicKey: String, emoji: String, react: Boolean) : this() {
        this.timestamp = timestamp
        this.publicKey = publicKey
        this.emoji = emoji
        this.react = react
    }

    fun toProto(): SignalServiceProtos.DataMessage.Reaction? {
        val timestamp = timestamp
        val publicKey = publicKey
        val emoji = emoji
        val react = react ?: true
        if (timestamp == null || publicKey == null || emoji == null) {
            Log.w(TAG, "Couldn't construct reaction proto from: $this")
            return null
        }
        val reactionProto = SignalServiceProtos.DataMessage.Reaction.newBuilder()
        reactionProto.id = timestamp
        reactionProto.author = publicKey
        reactionProto.emoji = emoji
        reactionProto.action = if (react) Action.REACT else Action.REMOVE
        // Build
        return try {
            reactionProto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct reaction proto from: $this")
            null
        }
    }

}