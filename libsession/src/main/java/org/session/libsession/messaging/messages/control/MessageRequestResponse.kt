package org.session.libsession.messaging.messages.control

import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Log

class MessageRequestResponse(val isApproved: Boolean) : ControlMessage() {

    override fun toProto(): SignalServiceProtos.Content? {
        val messageRequestResponseProto = SignalServiceProtos.MessageRequestResponse.newBuilder()
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
            return if (proto.hasMessageRequestResponse()) {
                MessageRequestResponse(proto.messageRequestResponse.isApproved)
            } else {
                return null
            }
        }
    }

}