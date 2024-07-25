package org.thoughtcrime.securesms.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.contacts.Contact
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.home.search.getSearchName

@OptIn(FlowPreview::class)
@HiltViewModel(assistedFactory = SelectContactsViewModel.Factory::class)
class SelectContactsViewModel @AssistedInject constructor(
    private val storageProtocol: StorageProtocol,
    private val configFactory: ConfigFactory,
    @Assisted private val onlySelectedFromAccountIDs: Set<String>?,
) : ViewModel() {
    // Input: The search query
    private val mutableSearchQuery = MutableStateFlow("")

    // Input: The selected contact account IDs
    private val mutableSelectedContactAccountIDs = MutableStateFlow(emptySet<String>())

    // Output: The search query
    val searchQuery: StateFlow<String> get() = mutableSearchQuery

    // Output: the contact items to display and select from
    val contacts: StateFlow<List<ContactItem>> = combine(
        observeContacts(),
        mutableSearchQuery.debounce(100L),
        mutableSelectedContactAccountIDs,
        ::filterContacts
    ).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Output
    val currentSelectedAccountIDs: List<String>
        get() = contacts.value
            .asSequence()
            .filter { it.selected }
            .map { it.accountID }
            .toList()

    private fun observeContacts() = (configFactory.configUpdateNotifications as Flow<Any>)
        .debounce(100L)
        .onStart { emit(Unit) }
        .map {
            val allContacts = withContext(Dispatchers.Default) {
                storageProtocol.getAllContacts()
            }

            if (onlySelectedFromAccountIDs == null) {
                allContacts
            } else {
                allContacts.filter { it.accountID in onlySelectedFromAccountIDs }
            }
        }


    private fun filterContacts(
        contacts: Collection<Contact>,
        query: String,
        selectedAccountIDs: Set<String>
    ): List<ContactItem> {
        return contacts
            .asSequence()
            .filter {
                it.name?.contains(query, ignoreCase = true) == true ||
                it.nickname?.contains(query, ignoreCase = true) == true
            }
            .map { contact ->
                ContactItem(
                    accountID = contact.accountID,
                    name = contact.getSearchName(),
                    selected = selectedAccountIDs.contains(contact.accountID)
                )
            }
            .toList()
    }

    fun onSearchQueryChanged(query: String) {
        mutableSearchQuery.value = query
    }

    fun onContactItemClicked(accountID: String) {
        val newSet = mutableSelectedContactAccountIDs.value.toHashSet()
        if (!newSet.remove(accountID)) {
            newSet.add(accountID)
        }
        mutableSelectedContactAccountIDs.value = newSet
    }

    @AssistedFactory
    interface Factory {
        fun create(onlySelectedFromAccountIDs: Set<String>?): SelectContactsViewModel
    }
}

data class ContactItem(
    val accountID: String,
    val name: String,
    val selected: Boolean,
)