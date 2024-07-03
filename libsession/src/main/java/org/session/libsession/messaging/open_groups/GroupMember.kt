package org.session.libsession.messaging.open_groups

data class GroupMember(
    val groupId: String,
    val profileId: String,
    val role: GroupMemberRole
)

enum class GroupMemberRole(val isModerator: Boolean = false) {
    STANDARD,
    ZOOMBIE,
    MODERATOR(true),
    ADMIN(true),
    HIDDEN_MODERATOR(true),
    HIDDEN_ADMIN(true),
}
