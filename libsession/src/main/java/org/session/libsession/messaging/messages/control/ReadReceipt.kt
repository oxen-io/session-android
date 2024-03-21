package org.session.libsession.messaging.messages.control

import org.session.libsession.messaging.messages.copyExpiration
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Log

class ReadReceipt() : ControlMessage() {
    var timestamps: List<Long>? = null

    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        val timestamps = timestamps ?: return false
        if (timestamps.isNotEmpty()) { return true }
        return false
    }

    companion object {
        const val TAG = "ReadReceipt"

        fun fromProto(proto: SignalServiceProtos.Content): ReadReceipt? {
            val receiptProto = if (proto.hasReceiptMessage()) proto.receiptMessage else return null
            if (receiptProto.type != SignalServiceProtos.ReceiptMessage.Type.READ) return null
            val timestamps = receiptProto.timestampList
            if (timestamps.isEmpty()) return null
            return ReadReceipt(timestamps = timestamps)
                    .copyExpiration(proto)
        }
    }

    constructor(timestamps: List<Long>?) : this() {
        this.timestamps = timestamps
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val timestamps = timestamps
        if (timestamps == null) {
            Log.w(TAG, "Couldn't construct read receipt proto from: $this")
            return null
        }

        return try {
            SignalServiceProtos.Content.newBuilder()
                .setReceiptMessage(
                    SignalServiceProtos.ReceiptMessage.newBuilder()
                        .setType(SignalServiceProtos.ReceiptMessage.Type.READ)
                        .addAllTimestamp(timestamps.asIterable()).build()
                ).applyExpiryMode()
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct read receipt proto from: $this")
            null
        }
    }
}