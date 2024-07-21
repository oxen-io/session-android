package org.session.libsession.messaging

data class BlindedIdMapping(
    val blindedId: String,
    val accountId: String?,
    val serverUrl: String,
    val serverId: String
)