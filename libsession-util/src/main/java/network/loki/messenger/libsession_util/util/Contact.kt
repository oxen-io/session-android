package network.loki.messenger.libsession_util.util

data class Contact(
    val id: String,
    var name: String? = null,
    var nickname: String? = null,
    var approved: Boolean = false,
    var approvedMe: Boolean = false,
    var blocked: Boolean = false,
    var profilePicture: UserPic? = null
)