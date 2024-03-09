package org.session.libsession.messaging.messages

import network.loki.messenger.libsession_util.util.ExpiryMode

data class ExpirationConfiguration(
    val threadId: Long = -1,
    val expiryMode: ExpiryMode = ExpiryMode.NONE,
    val updatedTimestampMs: Long = 0
) {
    val isEnabled = expiryMode.expirySeconds > 0

    companion object {
        val isNewConfigEnabled = true /* TODO: System.currentTimeMillis() > 1_676_851_200_000 // 13/02/2023 */
    }
}

data class ExpirationDatabaseMetadata(
    val threadId: Long = -1,
    val updatedTimestampMs: Long
)
