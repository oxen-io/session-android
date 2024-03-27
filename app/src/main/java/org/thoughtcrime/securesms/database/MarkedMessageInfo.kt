package org.thoughtcrime.securesms.database

import org.thoughtcrime.securesms.conversation.disappearingmessages.ExpiryType
import org.thoughtcrime.securesms.database.MessagingDatabase.SyncMessageId

data class MarkedMessageInfo(val syncMessageId: SyncMessageId, val expirationInfo: ExpirationInfo) {
    val expiryType get() = when {
        syncMessageId.timetamp == expirationInfo.expireStarted -> ExpiryType.AFTER_SEND
        expirationInfo.expiresIn > 0 -> ExpiryType.AFTER_READ
        else -> ExpiryType.NONE
    }

    val expiryMode get() = expiryType.mode(expirationInfo.expiresIn)
}
