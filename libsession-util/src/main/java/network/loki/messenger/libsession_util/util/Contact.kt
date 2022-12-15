package network.loki.messenger.libsession_util.util

data class Contact(
    val id: String,
    var name: String?,
    var nickname: String?,
    var approved: Boolean,
    var approvedMe: Boolean,
    var blocked: Boolean,
    var profilePicture: UserPic?
)