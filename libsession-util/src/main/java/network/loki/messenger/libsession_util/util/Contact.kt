package network.loki.messenger.libsession_util.util

data class Contact(
    val id: String,
    val name: String,
    val nickname: String,
    val approved: Boolean,
    val approvedMe: Boolean,
    val blocked: Boolean,
    val profilePicture: UserPic
)