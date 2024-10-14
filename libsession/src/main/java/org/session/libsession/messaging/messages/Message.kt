package org.session.libsession.messaging.messages

import com.google.protobuf.ByteString
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.protos.SignalServiceProtos.Content.ExpirationType

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
    var specifiedTtl: Long? = null

    var expiryMode: ExpiryMode = ExpiryMode.NONE

    open val coerceDisappearAfterSendToRead = false

    open val defaultTtl: Long = 14 * 24 * 60 * 60 * 1000
    open val ttl: Long get() = specifiedTtl ?: defaultTtl
    open val isSelfSendValid: Boolean = false

    companion object {
        fun getThreadId(message: Message, openGroupID: String?, storage: StorageProtocol, shouldCreateThread: Boolean): Long? {
            return storage.getThreadIdFor(message.senderOrSync, message.groupPublicKey, openGroupID, createThread = shouldCreateThread)
        }

        val Message.senderOrSync get() = when(this)  {
            is VisibleMessage -> syncTarget ?: sender!!
            is ExpirationTimerUpdate -> syncTarget ?: sender!!
            else -> sender!!
        }
    }

    open fun isValid(): Boolean =
        sentTimestamp?.let { it > 0 } != false
            && receivedTimestamp?.let { it > 0 } != false
            && sender != null
            && recipient != null

    abstract fun toProto(): SignalServiceProtos.Content?

    fun SignalServiceProtos.DataMessage.Builder.setGroupContext() {
        group = SignalServiceProtos.GroupContext.newBuilder().apply {
            id = GroupUtil.doubleEncodeGroupID(recipient!!).let(GroupUtil::getDecodedGroupIDAsData).let(ByteString::copyFrom)
            type = SignalServiceProtos.GroupContext.Type.DELIVER
        }.build()
    }

    fun SignalServiceProtos.Content.Builder.applyExpiryMode() = apply {
        expirationTimer = expiryMode.expirySeconds.toInt()
        expirationType = when (expiryMode) {
            is ExpiryMode.AfterSend -> ExpirationType.DELETE_AFTER_SEND
            is ExpiryMode.AfterRead -> ExpirationType.DELETE_AFTER_READ
            else -> ExpirationType.UNKNOWN
        }
    }
}

inline fun <reified M: Message> M.copyExpiration(proto: SignalServiceProtos.Content): M = apply {
    (proto.takeIf { it.hasExpirationTimer() }?.expirationTimer ?: proto.dataMessage?.expireTimer)?.let { duration ->
        expiryMode = when (proto.expirationType.takeIf { duration > 0 }) {
            ExpirationType.DELETE_AFTER_SEND -> ExpiryMode.AfterSend(duration.toLong())
            ExpirationType.DELETE_AFTER_READ -> ExpiryMode.AfterRead(duration.toLong())
            else -> ExpiryMode.NONE
        }
    }
}

fun SignalServiceProtos.Content.expiryMode(): ExpiryMode =
    (takeIf { it.hasExpirationTimer() }?.expirationTimer ?: dataMessage?.expireTimer)?.let { duration ->
        when (expirationType.takeIf { duration > 0 }) {
            ExpirationType.DELETE_AFTER_SEND -> ExpiryMode.AfterSend(duration.toLong())
            ExpirationType.DELETE_AFTER_READ -> ExpiryMode.AfterRead(duration.toLong())
            else -> ExpiryMode.NONE
        }
    } ?: ExpiryMode.NONE

/**
 * Apply ExpiryMode from the current setting.
 */
inline fun <reified M: Message> M.applyExpiryMode(thread: Long): M = apply {
    val storage = MessagingModuleConfiguration.shared.storage
    expiryMode = storage.getExpirationConfiguration(thread)?.expiryMode?.coerceSendToRead(coerceDisappearAfterSendToRead) ?: ExpiryMode.NONE
}
