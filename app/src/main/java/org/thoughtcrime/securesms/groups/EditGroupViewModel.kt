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
import network.loki.messenger.libsession_util.util.GroupDisplayInfo
import network.loki.messenger.libsession_util.util.GroupMember
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.contacts.Contact
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
) : ViewModel() {
    // Input/Output state
    private val mutableEditingName = MutableStateFlow<String?>(null)

    // Output: The name of the group being edited. Null if it's not in edit mode, not to be confused
    // with empty string, where it's a valid editing state.
    val editingName: StateFlow<String?> get() = mutableEditingName

    // Output: the source-of-truth group information. Other states are derived from this.
    private val groupInfo: StateFlow<Pair<GroupDisplayInfo, List<GroupMemberState>>?> =
        configFactory.configUpdateNotifications
            .filter { it.hexString == groupSessionId }
            .onStart { emit(AccountId(groupSessionId)) }
            .map { _ ->
                withContext(Dispatchers.Default) {
                    val currentUserId = checkNotNull(storage.getUserPublicKey()) {
                        "User public key is null"
                    }

                    val members = storage.getMembers(groupSessionId).map { member ->
                        createGroupMember(member, currentUserId)
                    }

                    val displayInfo = storage.getClosedGroupDisplayInfo(groupSessionId)
                        ?: return@withContext null

                    displayInfo to members
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    private fun createGroupMember(member: GroupMember, myAccountId: String): GroupMemberState {
        var status = ""
        var highlightStatus = false
        var name = member.name.orEmpty()

        when {
            member.sessionId == myAccountId -> {
                name = "You"
            }

            member.inviteFailed -> {
                status = "Invite Failed"
                highlightStatus = true
            }

            member.invitePending -> {
                status = "Inviting"
            }

            member.promotionFailed -> {
                status = "Promotion Failed"
                highlightStatus = true
            }

            member.promotionPending -> {
                status = "Promoting"
            }
        }

        return GroupMemberState(
            accountId = member.sessionId,
            name = name,
            showEdit = member.sessionId != myAccountId && member.admin,
            status = status,
            highlightStatus = highlightStatus
        )
    }


    fun onContactSelected(contacts: Set<Contact>) {
        storage.inviteClosedGroupMembers(groupSessionId, contacts.map { it.accountID })
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

data class GroupMemberState(
    val accountId: String,
    val name: String,
    val status: String,
    val highlightStatus: Boolean,
    val showEdit: Boolean,
)
