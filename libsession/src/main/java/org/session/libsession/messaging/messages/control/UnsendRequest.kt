package org.session.libsession.messaging.messages.control

import org.session.libsession.messaging.messages.copyExpiration
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Log

class UnsendRequest(var timestamp: Long? = null, var author: String? = null): ControlMessage() {

    override val isSelfSendValid: Boolean = true

    // region Validation
    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        return timestamp != null && author != null
    }
    // endregion

    companion object {
        const val TAG = "UnsendRequest"

        fun fromProto(proto: SignalServiceProtos.Content): UnsendRequest? =
            proto.takeIf { it.hasUnsendRequest() }?.unsendRequest?.run { UnsendRequest(timestamp, author) }?.copyExpiration(proto)
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val timestamp = timestamp
        val author = author
        if (timestamp == null || author == null) {
            Log.w(TAG, "Couldn't construct unsend request proto from: $this")
            return null
        }
        return try {
            SignalServiceProtos.Content.newBuilder()
                .setUnsendRequest(SignalServiceProtos.UnsendRequest.newBuilder().setTimestamp(timestamp).setAuthor(author).build())
                .applyExpiryMode()
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct unsend request proto from: $this")
            null
        }
    }

}