package org.session.libsession.messaging.messages

import com.google.protobuf.ByteString
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.protos.SignalServiceProtos

abstract class Message {
    var id: Long? = null
    var threadID: Long? = null
    var sentTimestamp: Long? = null
    var receivedTimestamp: Long? = null
    var recipient: String? = null
    var sender: String? = null
    var isSenderSelf: Boolean = false
    var groupPublicKey: String? = null
    var openGroupServerMessageID: Long? = null
    var serverHash: String? = null

    open val ttl: Long = 14 * 24 * 60 * 60 * 1000
    open val isSelfSendValid: Boolean = false

    companion object {
        fun getThreadId(message: Message, openGroupID: String?, storage: StorageProtocol, shouldCreateThread: Boolean): Long? {
            val senderOrSync = when (message) {
                is VisibleMessage -> message.syncTarget ?: message.sender!!
                is ExpirationTimerUpdate -> message.syncTarget ?: message.sender!!
                else -> message.sender!!
            }
            return storage.getThreadIdFor(senderOrSync, message.groupPublicKey, openGroupID, createThread = shouldCreateThread)
        }
    }

    open fun isValid(): Boolean {
        val sentTimestamp = sentTimestamp
        if (sentTimestamp != null && sentTimestamp <= 0) { return false }
        val receivedTimestamp = receivedTimestamp
        if (receivedTimestamp != null && receivedTimestamp <= 0) { return false }
        return sender != null && recipient != null
    }

    abstract fun toProto(): SignalServiceProtos.Content?

    fun setGroupContext(dataMessage: SignalServiceProtos.DataMessage.Builder) {
        val groupProto = SignalServiceProtos.GroupContext.newBuilder()
        val groupID = GroupUtil.doubleEncodeGroupID(recipient!!)
        groupProto.id = ByteString.copyFrom(GroupUtil.getDecodedGroupIDAsData(groupID))
        groupProto.type = SignalServiceProtos.GroupContext.Type.DELIVER
        dataMessage.group = groupProto.build()
    }

}