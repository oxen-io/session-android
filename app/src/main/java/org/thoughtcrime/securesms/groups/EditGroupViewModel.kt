package org.thoughtcrime.securesms.groups

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.GroupDisplayInfo
import network.loki.messenger.libsession_util.util.GroupMember
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.groups.GroupManagerV2
import org.session.libsession.utilities.ConfigUpdateNotification
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.dependencies.ConfigFactory

const val MAX_GROUP_NAME_LENGTH = 100

@HiltViewModel(assistedFactory = EditGroupViewModel.Factory::class)
class EditGroupViewModel @AssistedInject constructor(
    @Assisted private val groupId: AccountId,
    @ApplicationContext private val context: Context,
    private val storage: StorageProtocol,
    configFactory: ConfigFactory,
    private val groupManager: GroupManagerV2,
) : ViewModel() {
    // Input/Output state
    private val mutableEditingName = MutableStateFlow<String?>(null)

    // Input: invite/promote member's intermediate states. This is needed because we don't have
    // a state that we can map into in the config system. The config system only provides "sent", "failed", etc.
    // The intermediate states are needed to show the user that the operation is in progress, and the
    // states are limited to the view model (i.e. lost if the user navigates away). This is a trade-off
    // between the complexity of the config system and the user experience.
    private val memberPendingState = MutableStateFlow<Map<AccountId, MemberPendingState>>(emptyMap())

    // Output: The name of the group being edited. Null if it's not in edit mode, not to be confused
    // with empty string, where it's a valid editing state.
    val editingName: StateFlow<String?> get() = mutableEditingName

    // Output: the source-of-truth group information. Other states are derived from this.
    private val groupInfo: StateFlow<Pair<GroupDisplayInfo, List<GroupMemberState>>?> =
        combine(
            configFactory.configUpdateNotifications
                .filterIsInstance<ConfigUpdateNotification.GroupConfigsUpdated>()
                .filter { it.groupId == groupId }
                .onStart { emit(ConfigUpdateNotification.GroupConfigsUpdated(groupId)) },
            memberPendingState
        ) { _, pending ->
            withContext(Dispatchers.Default) {
                val currentUserId = AccountId(checkNotNull(storage.getUserPublicKey()) {
                    "User public key is null"
                })

                val displayInfo = storage.getClosedGroupDisplayInfo(groupId.hexString)
                    ?: return@withContext null

                val members = storage.getMembers(groupId.hexString)
                    .filterTo(mutableListOf()) { !it.removed }
                sortMembers(members, currentUserId)

                displayInfo to members.map { member ->
                    createGroupMember(
                        member = member,
                        myAccountId = currentUserId,
                        amIAdmin = displayInfo.isUserAdmin,
                        pendingState = pending[AccountId(member.sessionId)]
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Output: whether the group name can be edited. This is true if the group is loaded successfully.
    val canEditGroupName: StateFlow<Boolean> = groupInfo
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Output: The name of the group. This is the current name of the group, not the name being edited.
    val groupName: StateFlow<String> = groupInfo
        .map { it?.first?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

    // Output: the list of the members and their state in the group.
    val members: StateFlow<List<GroupMemberState>> = groupInfo
        .map { it?.second.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    // Output: whether we should show the "add members" button
    val showAddMembers: StateFlow<Boolean> = groupInfo
        .map { it?.first?.isUserAdmin == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    // Output: Intermediate states
    private val mutableInProgress = MutableStateFlow(false)
    val inProgress: StateFlow<Boolean> get() = mutableInProgress

    // Output: errors
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> get() = mutableError

    // Output:
    val excludingAccountIDsFromContactSelection: Set<AccountId>
        get() = groupInfo.value?.second?.mapTo(hashSetOf()) { it.accountId }.orEmpty()

    private fun createGroupMember(
        member: GroupMember,
        myAccountId: AccountId,
        amIAdmin: Boolean,
        pendingState: MemberPendingState?
    ): GroupMemberState {
        var status = ""
        var highlightStatus = false
        var name = member.name.orEmpty().ifEmpty { member.sessionId }

        when {
            member.sessionId == myAccountId.hexString -> {
                name = context.getString(R.string.you)
            }

            pendingState == MemberPendingState.Inviting -> {
                status = context.getString(R.string.groupInviteSending)
            }

            pendingState == MemberPendingState.Promoting -> {
                status = context.getString(R.string.groupInviteSending)
            }

            member.promotionPending -> {
                status = context.getString(R.string.adminPromotionSent)
            }

            member.invitePending -> {
                status = context.getString(R.string.groupInviteSent)
            }

            member.inviteFailed -> {
                status = context.getString(R.string.groupInviteFailed)
                highlightStatus = true
            }

            member.promotionFailed -> {
                status = context.getString(R.string.adminPromotionFailed)
                highlightStatus = true
            }
        }

        return GroupMemberState(
            accountId = AccountId(member.sessionId),
            name = name,
            canRemove = amIAdmin && member.sessionId != myAccountId.hexString && !member.isAdminOrBeingPromoted,
            canPromote = amIAdmin && member.sessionId != myAccountId.hexString && !member.isAdminOrBeingPromoted,
            canResendPromotion = amIAdmin && member.sessionId != myAccountId.hexString && member.promotionFailed,
            canResendInvite = amIAdmin && member.sessionId != myAccountId.hexString &&
                    (member.inviteFailed || member.invitePending),
            status = status,
            highlightStatus = highlightStatus
        )
    }

    private fun sortMembers(members: MutableList<GroupMember>, currentUserId: AccountId) {
        members.sortWith(
            compareBy(
                { !it.inviteFailed }, // Failed invite comes first (as false value is less than true)
                { memberPendingState.value[AccountId(it.sessionId)] != MemberPendingState.Inviting }, // "Sending invite" comes first
                { !it.invitePending }, // "Invite sent" comes first
                { !it.isAdminOrBeingPromoted }, // Admins come first
                { it.sessionId != currentUserId.hexString }, // Being myself comes first
                { it.name }, // Sort by name
                { it.sessionId } // Last resort: sort by account ID
            )
        )
    }

    fun onContactSelected(contacts: Set<AccountId>) {
        performGroupOperation {
            try {
                // Mark the contacts as pending
                memberPendingState.update { states ->
                    states + contacts.associateWith { MemberPendingState.Inviting }
                }

                groupManager.inviteMembers(
                    groupId,
                    contacts.toList(),
                    shareHistory = false
                )
            } finally {
                // Remove pending state (so the real state will be revealed)
                memberPendingState.update { states -> states - contacts }
            }
        }
    }

    fun onResendInviteClicked(contactSessionId: AccountId) {
        onContactSelected(setOf(contactSessionId))
    }

    fun onPromoteContact(memberSessionId: AccountId) {
        performGroupOperation {
            try {
                memberPendingState.update { states ->
                    states + (memberSessionId to MemberPendingState.Promoting)
                }

                groupManager.promoteMember(groupId, listOf(memberSessionId))
            } finally {
                memberPendingState.update { states -> states - memberSessionId }
            }
        }
    }

    fun onRemoveContact(contactSessionId: AccountId, removeMessages: Boolean) {
        performGroupOperation {
            groupManager.removeMembers(
                groupAccountId = groupId,
                removedMembers = listOf(contactSessionId),
                removeMessages = removeMessages
            )
        }
    }

    fun onResendPromotionClicked(memberSessionId: AccountId) {
        onPromoteContact(memberSessionId)
    }

    fun onEditNameClicked() {
        mutableEditingName.value = groupInfo.value?.first?.name.orEmpty()
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

        performGroupOperation {
            if (!newName.isNullOrBlank()) {
                groupManager.setName(groupId, newName)
                mutableEditingName.value = null
            }
        }
    }

    fun onDismissError() {
        mutableError.value = null
    }

    /**
     * Perform a group operation, such as inviting a member, removing a member.
     *
     * This is a helper function that encapsulates the common error handling and progress tracking.
     */
    private fun performGroupOperation(
        genericErrorMessage: (() -> String?)? = null,
        operation: suspend () -> Unit) {
        viewModelScope.launch {
            mutableInProgress.value = true

            // We need to use GlobalScope here because we don't want
            // any group operation to be cancelled when the view model is cleared.
            @Suppress("OPT_IN_USAGE")
            val task = GlobalScope.async {
                operation()
            }

            try {
                task.await()
            } catch (e: Exception) {
                mutableError.value = genericErrorMessage?.invoke()
                    ?: context.getString(R.string.errorUnknown)
            } finally {
                mutableInProgress.value = false
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(groupId: AccountId): EditGroupViewModel
    }
}

private enum class MemberPendingState {
    Inviting,
    Promoting,
}

data class GroupMemberState(
    val accountId: AccountId,
    val name: String,
    val status: String,
    val highlightStatus: Boolean,
    val canResendInvite: Boolean,
    val canResendPromotion: Boolean,
    val canRemove: Boolean,
    val canPromote: Boolean,
) {
    val canEdit: Boolean get() = canRemove || canPromote || canResendInvite || canResendPromotion
}
