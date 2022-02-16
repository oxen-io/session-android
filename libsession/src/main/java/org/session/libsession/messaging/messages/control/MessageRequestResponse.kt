package org.session.libsession.messaging.messages.control

import com.google.protobuf.ByteString
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.toHexString

class MessageRequestResponse(val publicKey: String, val isApproved: Boolean) : ControlMessage() {

    override val isSelfSendValid: Boolean = true

    override fun toProto(): SignalServiceProtos.Content? {
        val messageRequestResponseProto = SignalServiceProtos.MessageRequestResponse.newBuilder()
            .setPublicKey(ByteString.copyFrom(Hex.fromStringCondensed(publicKey)))
            .setIsApproved(isApproved)
        return try {
            SignalServiceProtos.Content.newBuilder()
                .setMessageRequestResponse(messageRequestResponseProto.build())
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct message request response proto from: $this")
            null
        }
    }

    companion object {
        const val TAG = "MessageRequestResponse"

        fun fromProto(proto: SignalServiceProtos.Content): MessageRequestResponse? {
            val messageRequestResponseProto = if (proto.hasMessageRequestResponse()) proto.messageRequestResponse else return null
            val publicKey = messageRequestResponseProto.publicKey.toByteArray().toHexString()
            val isApproved = messageRequestResponseProto.isApproved
            return MessageRequestResponse(publicKey, isApproved)
        }
    }

}