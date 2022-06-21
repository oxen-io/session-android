package org.session.libsession.messaging.messages.visible

import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.protos.SignalServiceProtos.DataMessage.Reaction.Action
import org.session.libsignal.utilities.Log

class Reaction() {
    var timestamp: Long? = 0
    var publicKey: String? = null
    var emoji: String? = null
    var react: Boolean? = true
    var serverId: String? = null
    var dateSent: Long? = 0
    var dateReceived: Long? = 0

    fun isValid(): Boolean {
        return (timestamp != null && publicKey != null)
    }

    companion object {
        const val TAG = "Quote"

        fun fromProto(proto: SignalServiceProtos.DataMessage.Reaction): Reaction? {
            val react = proto.action == Action.REACT
            return Reaction(proto.id, proto.author, proto.emoji, react)
        }

        fun from(timestamp: Long, author: String, emoji: String): Reaction? {
            return Reaction(timestamp, author, emoji, true)
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