package network.loki.messenger.libsession_util.util

import org.session.libsignal.utilities.SessionId

data class GroupDisplayInfo(
    val id: SessionId,
    val created: Long?,
    val expiryTimer: Long?,
    val name: String,
    val description: String?,
    val destroyed: Boolean,
    val profilePic: UserPic,
    val isUserAdmin: Boolean
)