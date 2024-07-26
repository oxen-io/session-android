package org.thoughtcrime.securesms.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import network.loki.messenger.libsession_util.util.GroupMember
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.jobs.InviteContactsJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.dependencies.ConfigFactory

const val MAX_GROUP_NAME_LENGTH = 100

@HiltViewModel(assistedFactory = EditGroupViewModel.Factory::class)
class EditGroupViewModel @AssistedInject constructor(
    @Assisted private val groupSessionId: String,
    private val storage: StorageProtocol,
    configFactory: ConfigFactory
): ViewModel() {
    // Input/Output state
    private val mutableEditingName = MutableStateFlow<String?>(null)

    // Output: The name of the group being edited. Null if it's not in edit mode, not to be confused
    // with empty string, where it's a valid editing state.
    val editingName: StateFlow<String?> get() = mutableEditingName

    // Output: the overall view state
    val viewState = configFactory.configUpdateNotifications
        .filter { it.hexString == groupSessionId }
        .onStart { emit(AccountId(groupSessionId)) }
        .map { _ ->
            withContext(Dispatchers.Default) {
                val currentUserId = checkNotNull(storage.getUserPublicKey()) {
                    "User public key is null"
                }

                val members = storage.getMembers(groupSessionId).map { member ->
                    MemberViewModel(
                        memberName = member.name,
                        memberSessionId = member.sessionId,
                        memberState = memberStateOf(member),
                        currentUser = member.sessionId == currentUserId
                    )
                }

                val displayInfo = storage.getClosedGroupDisplayInfo(groupSessionId)
                EditGroupViewState(
                    groupName = displayInfo?.name.orEmpty(),
                    groupDescription = displayInfo?.description,
                    memberStateList = members,
                    admin = displayInfo?.isUserAdmin == true,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EditGroupViewState())

    fun onContactSelected(contactAccountIDs: List<String>) {
        storage.inviteClosedGroupMembers(groupSessionId, contactAccountIDs)
    }

    fun onReInviteContact(contactSessionId: String) {
        JobQueue.shared.add(InviteContactsJob(groupSessionId, arrayOf(contactSessionId)))
    }

    fun onPromoteContact(contactSessionId: String) {
        storage.promoteMember(groupSessionId, arrayOf(contactSessionId))
    }

    fun onRemoveContact(contactSessionId: String) {
        storage.removeMember(groupSessionId, arrayOf(contactSessionId))
    }

    fun onEditNameClicked() {
        mutableEditingName.value = viewState.value.groupName
    }

    fun onCancelEditingNameClicked() {
        mutableEditingName.value = null
    }

    fun onEditingNameChanged(value: String) {
        // Cut off the group name so we don't exceed max length
        if (value.length > MAX_GROUP_NAME_LENGTH) {
            mutableEditingName.value = value.substring(0, MAX_GROUP_NAME_LENGTH)
        } else {
            mutableEditingName.value = value
        }
    }

    fun onEditNameConfirmClicked() {
        val newName = mutableEditingName.value
        if (newName != null) {
            storage.setName(groupSessionId, newName.trim())
            mutableEditingName.value = null
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(groupSessionId: String): EditGroupViewModel
    }

}

data class MemberViewModel(
    val memberName: String?,
    val memberSessionId: String,
    val memberState: MemberState,
    val currentUser: Boolean,
)

enum class MemberState {
    InviteSent,
    Inviting, // maybe just use these in view
    InviteFailed,
    PromotionSent,
    Promoting, // maybe just use these in view
    PromotionFailed,
    Admin,
    Member
}

fun memberStateOf(member: GroupMember): MemberState = when {
    member.inviteFailed -> MemberState.InviteFailed
    member.invitePending -> MemberState.InviteSent
    member.promotionFailed -> MemberState.PromotionFailed
    member.promotionPending -> MemberState.PromotionSent
    member.admin -> MemberState.Admin
    else -> MemberState.Member
}

data class EditGroupViewState(
    val groupName: String = "",
    val groupDescription: String? = null,
    val memberStateList: List<MemberViewModel> = emptyList(),
    private val admin: Boolean = false
) {
    val canEditName: Boolean
        get() = admin

    val canInvite: Boolean
        get() = admin

    val canPromote: Boolean
        get() = admin
}
