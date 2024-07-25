package org.thoughtcrime.securesms.groups

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.Storage
import javax.inject.Inject

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val storage: Storage,
) : ViewModel() {

    private inline fun <reified T> MutableLiveData<T>.update(body: T.() -> T) {
        this.postValue(body(this.value!!))
    }

    private val _viewState = MutableLiveData(ViewState.DEFAULT.copy())

    val viewState: LiveData<ViewState> = _viewState

    fun updateState(stateUpdate: StateUpdate) {
        when (stateUpdate) {
            is StateUpdate.AddContacts -> _viewState.update { copy(members = members + contacts.value.orEmpty().filter { it.accountID in stateUpdate.accountIDs }) }
            is StateUpdate.Description -> _viewState.update { copy(description = stateUpdate.value) }
            is StateUpdate.Name -> _viewState.update { copy(name = stateUpdate.value) }
            is StateUpdate.RemoveContact -> _viewState.update { copy(members = members - stateUpdate.value) }
            StateUpdate.Create -> {
                viewModelScope.launch(Dispatchers.IO) {
                    tryCreateGroup()
                }
            }
        }
    }

    val contacts
        get() = liveData { emit(storage.getAllContacts()) }

    val availableContactAccountIDsToSelect: List<String>
        get() = contacts.value.orEmpty().filterNot { contact ->
            _viewState.value?.members.orEmpty().any { it.accountID == contact.accountID }
        }.map { it.accountID }

    private fun tryCreateGroup() {

        val currentState = _viewState.value!!

        _viewState.postValue(currentState.copy(isLoading = true, error = null))

        val name = currentState.name
        val description = currentState.description
        val members = currentState.members.toMutableSet()

        // do some validation
        // need a name
        if (name.isEmpty()) {
            return _viewState.postValue(
                currentState.copy(isLoading = false, error = R.string.error)
            )
        }

        if (members.size <= 1) {
            _viewState.postValue(
                currentState.copy(
                    isLoading = false,
                    error = R.string.activity_create_closed_group_not_enough_group_members_error
                )
            )
        }

        // make a group
        val newGroup = storage.createNewGroup(name, description, members)
        if (!newGroup.isPresent) {
            // show a generic couldn't create or something?
            return _viewState.postValue(currentState.copy(isLoading = false, error = null))
        } else {
            return _viewState.postValue(currentState.copy(
                isLoading = false,
                error = null,
                createdGroup = newGroup.get())
            )
        }
    }

    fun onContactsSelected(accountIDs: List<String>) {
        updateState(StateUpdate.AddContacts(accountIDs))
    }
}

data class ViewState(
    val isLoading: Boolean,
    @StringRes val error: Int?,
    val name: String = "",
    val description: String = "",
    val members: List<Contact> = emptyList(),
    val createdGroup: Recipient? = null,
    ) {

    val canCreate
        get() = name.isNotEmpty() && members.isNotEmpty()

    companion object {
        val DEFAULT = ViewState(false, null)
    }

}

sealed class StateUpdate {
    data object Create: StateUpdate()
    data class Name(val value: String): StateUpdate()
    data class Description(val value: String): StateUpdate()
    data class RemoveContact(val value: Contact): StateUpdate()
    data class AddContacts(val accountIDs: List<String>): StateUpdate()
}