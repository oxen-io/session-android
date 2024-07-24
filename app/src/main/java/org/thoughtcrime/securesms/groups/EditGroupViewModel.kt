package org.thoughtcrime.securesms.groups

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.GroupMember
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.jobs.InviteContactsJob
import org.session.libsession.messaging.jobs.JobQueue
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.ui.theme.LocalColors

class EditGroupViewModel @AssistedInject constructor(
    @Assisted private val groupSessionId: String,
    private val storage: StorageProtocol,
    private val configFactory: ConfigFactory
): ViewModel() {

    val viewState = viewModelScope.launchMolecule(RecompositionMode.Immediate) {

        val currentUserId = rememberSaveable {
            storage.getUserPublicKey()!!
        }

        fun getMembers() = storage.getMembers(groupSessionId).map { member ->
            MemberViewModel(
                memberName = member.name,
                memberSessionId = member.sessionId,
                currentUser = member.sessionId == currentUserId,
                memberState = memberStateOf(member)
            )
        }

        val closedGroupInfo by configFactory.configUpdateNotifications.map { it.hexString} .filter(groupSessionId::equals)
            .map {
                storage.getClosedGroupDisplayInfo(it)!! to getMembers()
            }.collectAsState(initial = storage.getClosedGroupDisplayInfo(groupSessionId)!! to getMembers())

        val (closedGroup, closedGroupMembers) = closedGroupInfo

        val name = closedGroup.name
        val description = closedGroup.description

        EditGroupState(
            EditGroupViewState(
                groupName = name.orEmpty(),
                groupDescription = description,
                memberStateList = closedGroupMembers,
                admin = closedGroup.isUserAdmin
            )
        ) { event ->
            when (event) {
                is EditGroupEvent.InviteContacts -> {
                    val sessionIds = event.contacts
                    storage.inviteClosedGroupMembers(
                        groupSessionId,
                        sessionIds.contacts.map(Contact::accountID)
                    )
                }

                is EditGroupEvent.ReInviteContact -> {
                    // do a buffer
                    JobQueue.shared.add(
                        InviteContactsJob(
                            groupSessionId,
                            arrayOf(event.contactSessionId)
                        )
                    )
                }

                is EditGroupEvent.PromoteContact -> {
                    // do a buffer
                    storage.promoteMember(groupSessionId, arrayOf(event.contactSessionId))
                }

                is EditGroupEvent.RemoveContact -> {
                    storage.removeMember(groupSessionId, arrayOf(event.contactSessionId))
                }

                is EditGroupEvent.ChangeName -> {
                    storage.setName(groupSessionId, event.newName)
                }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(groupSessionId: String): EditGroupViewModel
    }

}

data class EditGroupState(
    val viewState: EditGroupViewState,
    val eventSink: (EditGroupEvent) -> Unit
)

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

@Composable
fun MemberState.toDisplayString(): String? = when(this) {
    MemberState.InviteSent -> stringResource(id = R.string.groupMemberStateInviteSent)
    MemberState.Inviting -> stringResource(id = R.string.groupMemberStateInviting)
    MemberState.InviteFailed -> stringResource(id = R.string.groupMemberStateInviteFailed)
    MemberState.PromotionSent -> stringResource(id = R.string.groupMemberStatePromotionSent)
    MemberState.Promoting -> stringResource(id = R.string.groupMemberStatePromoting)
    MemberState.PromotionFailed -> stringResource(id = R.string.groupMemberStatePromotionFailed)
    else -> null
}

@Composable
fun MemberState.toDisplayColor(): Color = when (this) {
    MemberState.InviteFailed, MemberState.PromotionFailed -> LocalColors.current.danger
    else -> LocalColors.current.text
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
    val groupName: String,
    val groupDescription: String?,
    val memberStateList: List<MemberViewModel>,
    val admin: Boolean
)

sealed class EditGroupEvent {
    data class InviteContacts(val context: Context,
                              val contacts: ContactList
    ): EditGroupEvent()
    data class ReInviteContact(val contactSessionId: String): EditGroupEvent()
    data class PromoteContact(val contactSessionId: String): EditGroupEvent()
    data class RemoveContact(val contactSessionId: String): EditGroupEvent()
    data class ChangeName(val newName: String): EditGroupEvent()
}