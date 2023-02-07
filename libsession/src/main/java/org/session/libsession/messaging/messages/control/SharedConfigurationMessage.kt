package org.session.libsession.messaging.messages.control

import com.google.protobuf.ByteString
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.protos.SignalServiceProtos.SharedConfigMessage

class SharedConfigurationMessage(val kind: SharedConfigMessage.Kind, val data: ByteArray, val seqNo: Long): ControlMessage() {

    override val ttl: Long = 30 * 24 * 60 * 60 * 1000L
    override val isSelfSendValid: Boolean = true

    companion object {
        fun fromProto(proto: SignalServiceProtos.Content): SharedConfigurationMessage? {
            if (!proto.hasSharedConfigMessage()) return null
            val sharedConfig = proto.sharedConfigMessage
            if (!sharedConfig.hasKind() || !sharedConfig.hasData()) return null
            return SharedConfigurationMessage(sharedConfig.kind, sharedConfig.data.toByteArray(), sharedConfig.seqno)
        }
    }

    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        return data.isNotEmpty() && seqNo >= 0
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val sharedConfigurationMessage = SharedConfigMessage.newBuilder()
            .setKind(kind)
            .setSeqno(seqNo)
            .setData(ByteString.copyFrom(data))
            .build()
        return SignalServiceProtos.Content.newBuilder()
            .setSharedConfigMessage(sharedConfigurationMessage)
            .build()
    }
}