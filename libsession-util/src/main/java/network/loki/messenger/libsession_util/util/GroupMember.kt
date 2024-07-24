package network.loki.messenger.libsession_util.util

typealias InviteStatus = Int
typealias PromotionStatus = Int
typealias RemovedStatus = Int

const val INVITE_STATUS_SENT = 1
const val INVITE_STATUS_FAILED = 2

const val REMOVED_MEMBER = 1
const val REMOVED_MEMBER_AND_MESSAGES = 2

data class GroupMember(
    val sessionId: String,
    val name: String? = null,
    val profilePicture: UserPic = UserPic.DEFAULT,
    val admin: Boolean = false,
    val supplement: Boolean = false,
    private val inviteStatus: InviteStatus = 0,
    private val promotionStatus: PromotionStatus = 0,
    private val removedStatus: RemovedStatus = 0,
) {
    val accepted: Boolean get() = inviteStatus == 0 && !supplement
    val invitePending: Boolean get() = inviteStatus > 0
    val inviteFailed: Boolean get() = inviteStatus == INVITE_STATUS_FAILED

    val promotionPending: Boolean get() = !admin && promotionStatus > 0
    val promotionFailed: Boolean get() = !admin && promotionStatus == INVITE_STATUS_FAILED
    val promoted: Boolean get() = admin || promotionPending
    val removed: Boolean get() = removedStatus > 0
    val shouldRemoveMessages: Boolean get() = removedStatus == REMOVED_MEMBER_AND_MESSAGES

    fun setPromoteSent(): GroupMember
        = copy(promotionStatus = INVITE_STATUS_SENT)

    fun setPromoteFailed(): GroupMember
        = copy(promotionStatus = INVITE_STATUS_FAILED)

    fun setRemoved(alsoRemoveMessages: Boolean): GroupMember
        = copy(removedStatus = if (alsoRemoveMessages) REMOVED_MEMBER_AND_MESSAGES else REMOVED_MEMBER)

    fun setAccepted(): GroupMember
        = copy(inviteStatus = 0, supplement = false)

    fun setInvited(): GroupMember
        = copy(inviteStatus = INVITE_STATUS_SENT)

    fun setInviteFailed(): GroupMember = copy(inviteStatus = INVITE_STATUS_FAILED)
    fun setPromoteSuccess(): GroupMember = copy(admin = true, promotionStatus = 0)
}