package org.session.libsession.messaging.messages

import network.loki.messenger.libsession_util.util.ExpiryMode

data class ExpirationConfiguration(
    val threadId: Long = -1,
    val expiryMode: ExpiryMode = ExpiryMode.NONE,
    val updatedTimestampMs: Long = 0
) {
    val isEnabled = expiryMode.expirySeconds > 0

    companion object {
        val isNewConfigEnabled = true
    }
}

data class ExpirationDatabaseMetadata(
    val threadId: Long = -1,
    val updatedTimestampMs: Long
)
