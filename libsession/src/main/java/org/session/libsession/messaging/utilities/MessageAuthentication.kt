package org.session.libsession.messaging.utilities

import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.AccountId

object MessageAuthentication {
    fun buildInfoChangeVerifier(
        changeType: SignalServiceProtos.DataMessage.GroupUpdateInfoChangeMessage.Type,
        timestamp: Long): ByteArray {
        return "INFO_CHANGE${changeType.number}$timestamp".toByteArray()
    }

    fun buildDeleteMemberContentSignature(
        memberIds: Iterable<AccountId>,
        messageHashes: Iterable<String>,
        timestamp: Long
    ): ByteArray {
        return buildString {
            append("DELETE_CONTENT")
            append(timestamp)
            memberIds.forEach { append(it.hexString) }
            messageHashes.forEach(this::append)
        }.toByteArray()
    }

    fun buildMemberChangeSignature(
        changeType: SignalServiceProtos.DataMessage.GroupUpdateMemberChangeMessage.Type,
        timestamp: Long
    ): ByteArray {
        return "MEMBER_CHANGE${changeType.number}$timestamp".toByteArray()
    }

    fun buildGroupInviteSignature(
        memberId: AccountId,
        timestamp: Long
    ): ByteArray {
        return "INVITE${memberId.hexString}$timestamp".toByteArray()
    }
}