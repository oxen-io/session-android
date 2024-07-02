package network.loki.messenger.libsession_util.util

data class GroupMember(
    val sessionId: String,
    val name: String? = null,
    val profilePicture: UserPic = UserPic.DEFAULT,
    val inviteFailed: Boolean = false,
    val invitePending: Boolean = false,
    val admin: Boolean = false,
    val promotionFailed: Boolean = false,
    val promotionPending: Boolean = false,
)