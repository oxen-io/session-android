package network.loki.messenger.libsession_util.util

import org.session.libsignal.utilities.AccountId

data class GroupDisplayInfo(
    val id: AccountId,
    val created: Long?,
    val expiryTimer: Long?,
    val name: String?,
    val description: String?,
    val destroyed: Boolean,
    val profilePic: UserPic,
    val isUserAdmin: Boolean
)