package org.thoughtcrime.securesms.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.session.libsession.database.StorageProtocol
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import javax.inject.Inject


@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    configFactory: ConfigFactory,
    private val storage: StorageProtocol,
): ViewModel() {
    // Child view model to handle contact selection logic
    val selectContactsViewModel = SelectContactsViewModel(
        storage = storage,
        configFactory = configFactory,
        excludingAccountIDs = emptySet(),
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
        viewModelScope.launch {
            val groupName = groupName.value.trim()
            if (groupName.isBlank()) {
                mutableGroupNameError.value = "Group name cannot be empty"
                return@launch
            }

            val selected = selectContactsViewModel.currentSelected
            if (selected.isEmpty()) {
                mutableEvents.emit(CreateGroupEvent.Error("Please select at least one contact"))
                return@launch
            }

            mutableIsLoading.value = true

            val recipient = withContext(Dispatchers.Default) {
                storage.createNewGroup(groupName, "", selected)
            }

            if (recipient.isPresent) {
                val threadId = withContext(Dispatchers.Default) { storage.getOrCreateThreadIdFor(recipient.get().address) }
                mutableEvents.emit(CreateGroupEvent.NavigateToConversation(threadId))
            } else {
                mutableEvents.emit(CreateGroupEvent.Error("Failed to create group"))
            }

            mutableIsLoading.value = false
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
    data class NavigateToConversation(val threadID: Long): CreateGroupEvent

    data class Error(val message: String): CreateGroupEvent
}