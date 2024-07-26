package org.thoughtcrime.securesms.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import javax.inject.Inject


@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    configFactory: ConfigFactory,
    storage: Storage,
): ViewModel() {
    // Child view model to handle contact selection logic
    val selectContactsViewModel = SelectContactsViewModel(
        storageProtocol = storage,
        configFactory = configFactory,
        onlySelectedFromAccountIDs = null,
        scope = viewModelScope,
    )

    // Input: group name
    private val mutableGroupName = MutableStateFlow("")
    private val mutableGroupNameError = MutableStateFlow("")

    // Output: group name
    val groupName: StateFlow<String> get() = mutableGroupName
    val groupNameError: StateFlow<String> get() = mutableGroupNameError

    // Output: loading state
    private val mutableIsLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> get() = mutableIsLoading

    // Events
    private val mutableEvents = MutableSharedFlow<CreateGroupEvent>()
    val events: SharedFlow<CreateGroupEvent> get() = mutableEvents

    fun onCreateClicked() {
        if (groupName.value.trim().isBlank()) {
            mutableGroupNameError.value = "Group name cannot be empty"
            return
        }
    }

    fun onGroupNameChanged(name: String) {
        mutableGroupName.value = if (name.length > MAX_GROUP_NAME_LENGTH) {
            name.substring(0, MAX_GROUP_NAME_LENGTH)
        } else {
            name
        }

        mutableGroupNameError.value = ""
    }
}

sealed interface CreateGroupEvent {
    data class NavigateToConversation(val groupAccountId: String): CreateGroupEvent
}