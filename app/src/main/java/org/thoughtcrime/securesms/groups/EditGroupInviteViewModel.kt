package org.thoughtcrime.securesms.groups

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.contacts.Contact

@HiltViewModel(assistedFactory = EditGroupInviteViewModel.Factory::class)
class EditGroupInviteViewModel @AssistedInject constructor(
    @Assisted private val groupSessionId: String,
    private val storage: StorageProtocol
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(groupSessionId: String): EditGroupInviteViewModel
    }

}

data class EditGroupInviteState(
    val viewState: EditGroupInviteViewState,
)

data class EditGroupInviteViewState(
    val currentMembers: List<GroupMemberState>,
    val allContacts: Set<Contact>
)