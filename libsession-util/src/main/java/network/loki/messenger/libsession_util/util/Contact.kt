package network.loki.messenger.libsession_util.util

data class Contact(
    val id: String,
    val name: String = "",
    val nickname: String = "",
    val approved: Boolean = false,
    val approvedMe: Boolean = false,
    val blocked: Boolean = false,
    val profilePicture: UserPic = UserPic.DEFAULT,
    val priority: Int = 0,
    val expiryMode: ExpiryMode
)