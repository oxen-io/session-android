package org.thoughtcrime.securesms.groups

import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.contacts.Contact

class EditGroupInviteViewModel @AssistedInject constructor(
    @Assisted private val groupSessionId: String,
    private val storage: StorageProtocol
): ViewModel() {

    val viewState = viewModelScope.launchMolecule(RecompositionMode.Immediate) {

        val currentUserId = rememberSaveable {
            storage.getUserPublicKey()!!
        }

        val contacts = remember {
            storage.getAllContacts()
        }

        val closedGroupMembers = remember {
            storage.getMembers(groupSessionId).map { member ->
                MemberViewModel(
                    memberName = member.name,
                    memberSessionId = member.sessionId,
                    currentUser = member.sessionId == currentUserId,
                    memberState = memberStateOf(member)
                )
            }
        }

        EditGroupInviteState(
            EditGroupInviteViewState(closedGroupMembers, contacts)
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(groupSessionId: String): EditGroupInviteViewModel
    }

}

data class EditGroupInviteState(
    val viewState: EditGroupInviteViewState,
)

data class EditGroupInviteViewState(
    val currentMembers: List<MemberViewModel>,
    val allContacts: Set<Contact>
)