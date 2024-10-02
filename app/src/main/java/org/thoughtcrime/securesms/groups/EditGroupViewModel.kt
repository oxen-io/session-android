package org.thoughtcrime.securesms.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.libsession_util.util.GroupDisplayInfo
import network.loki.messenger.libsession_util.util.GroupMember
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.groups.GroupManagerV2
import org.session.libsession.messaging.jobs.InviteContactsJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.dependencies.ConfigFactory

const val MAX_GROUP_NAME_LENGTH = 100

@HiltViewModel(assistedFactory = EditGroupViewModel.Factory::class)
class EditGroupViewModel @AssistedInject constructor(
    @Assisted private val groupSessionId: String,
    private val storage: StorageProtocol,
    configFactory: ConfigFactory,
    private val groupManager: GroupManagerV2,
) : ViewModel() {
    // Input/Output state
    private val mutableEditingName = MutableStateFlow<String?>(null)

    // Output: The name of the group being edited. Null if it's not in edit mode, not to be confused
    // with empty string, where it's a valid editing state.
    val editingName: StateFlow<String?> get() = mutableEditingName

    // Output: the source-of-truth group information. Other states are derived from this.
    private val groupInfo: StateFlow<Pair<GroupDisplayInfo, List<GroupMemberState>>?> =
        (configFactory.configUpdateNotifications as Flow<Any>)
            .onStart { emit(Unit) }
            .map {
                withContext(Dispatchers.Default) {
                    val currentUserId = checkNotNull(storage.getUserPublicKey()) {
                        "User public key is null"
                    }

                    val displayInfo = storage.getClosedGroupDisplayInfo(groupSessionId)
                        ?: return@withContext null

                    val members = storage.getMembers(groupSessionId)
                        .asSequence()
                        .filter { !it.removed }
                        .mapTo(mutableListOf()) { member ->
                            createGroupMember(
                                member = member,
                                myAccountId = currentUserId,
                                amIAdmin = displayInfo.isUserAdmin,
                            )
                        }

                    sortMembers(members, currentUserId)

                    displayInfo to members
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
    val excludingAccountIDsFromContactSelection: Set<String>
        get() = groupInfo.value?.second?.mapTo(hashSetOf()) { it.accountId }.orEmpty()

    private fun createGroupMember(
        member: GroupMember,
        myAccountId: String,
        amIAdmin: Boolean,
    ): GroupMemberState {
        var status = ""
        var highlightStatus = false
        var name = member.name.orEmpty()

        when {
            member.sessionId == myAccountId -> {
                name = "You"
            }

            member.promotionPending -> {
                status = "Promotion sent"
            }

            member.invitePending -> {
                status = "Invite Sent"
            }

            member.inviteFailed -> {
                status = "Invite Failed"
                highlightStatus = true
            }

            member.promotionFailed -> {
                status = "Promotion Failed"
                highlightStatus = true
            }
        }

        return GroupMemberState(
            accountId = member.sessionId,
            name = name,
            canRemove = amIAdmin && member.sessionId != myAccountId && !member.isAdminOrBeingPromoted,
            canPromote = amIAdmin && member.sessionId != myAccountId && !member.isAdminOrBeingPromoted,
            canResendPromotion = amIAdmin && member.sessionId != myAccountId && member.promotionFailed,
            canResendInvite = amIAdmin && member.sessionId != myAccountId && member.inviteFailed,
            status = status,
            highlightStatus = highlightStatus
        )
    }

    private fun sortMembers(members: MutableList<GroupMemberState>, currentUserId: String) {
        // Order or members:
        // 1. Current user always comes first
        // 2. Then sort by name
        // 3. Then sort by account ID
        members.sortWith(
            compareBy(
                { it.accountId != currentUserId },
                { it.name },
                { it.accountId }
            )
        )
    }

    fun onContactSelected(contacts: Set<Contact>) {
        performGroupOperation {
            groupManager.inviteMembers(
                AccountId(hexString = groupSessionId),
                contacts.map { AccountId(it.accountID) },
                shareHistory = true
            )
        }
    }

    fun onResendInviteClicked(contactSessionId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            JobQueue.shared.add(InviteContactsJob(groupSessionId, arrayOf(contactSessionId)))
        }
    }

    fun onPromoteContact(memberSessionId: String) {
        performGroupOperation {
            groupManager.promoteMember(AccountId(groupSessionId), listOf(AccountId(memberSessionId)))
        }
    }

    fun onRemoveContact(contactSessionId: String, removeMessages: Boolean) {
        performGroupOperation {
            groupManager.removeMembers(
                groupAccountId = AccountId(groupSessionId),
                removedMembers = listOf(AccountId(contactSessionId)),
                removeMessages = removeMessages
            )
        }
    }

    fun onResendPromotionClicked(memberSessionId: String) {
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
                groupManager.setName(AccountId(groupSessionId), newName)
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
    private fun performGroupOperation(operation: suspend () -> Unit) {
        viewModelScope.launch {
            mutableInProgress.value = true

            // We need to use GlobalScope here because we don't want
            // "removeMember" to be cancelled when the view model is cleared. This operation
            // is expected to complete even if the view model is cleared.
            val task = GlobalScope.launch {
                operation()
            }

            try {
                task.join()
            } catch (e: Exception) {
                mutableError.value = e.localizedMessage.orEmpty()
            } finally {
                mutableInProgress.value = false
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(groupSessionId: String): EditGroupViewModel
    }
}

data class GroupMemberState(
    val accountId: String,
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