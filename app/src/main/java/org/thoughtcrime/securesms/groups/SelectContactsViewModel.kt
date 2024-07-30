package org.thoughtcrime.securesms.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private val storage: StorageProtocol,
    private val configFactory: ConfigFactory,
    @Assisted private val excludingAccountIDs: Set<String>,
    @Assisted private val scope: CoroutineScope
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
    val currentSelected: Set<Contact>
        get() = contacts.value
            .asSequence()
            .filter { it.selected }
            .map { it.contact }
            .toSet()

    override fun onCleared() {
        super.onCleared()

        scope.cancel()
    }

    private fun observeContacts() = (configFactory.configUpdateNotifications as Flow<Any>)
        .debounce(100L)
        .onStart { emit(Unit) }
        .map {
            withContext(Dispatchers.Default) {
                val allContacts = storage.getAllContacts()

                if (excludingAccountIDs.isEmpty()) {
                    allContacts
                } else {
                    allContacts.filterNot { it.accountID in excludingAccountIDs }
                }
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
                query.isBlank() ||
                it.name?.contains(query, ignoreCase = true) == true ||
                it.nickname?.contains(query, ignoreCase = true) == true
            }
            .map { contact ->
                ContactItem(
                    contact = contact,
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
        fun create(
            excludingAccountIDs: Set<String> = emptySet(),
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        ): SelectContactsViewModel
    }
}

data class ContactItem(
    val contact: Contact,
    val selected: Boolean,
) {
    val accountID: String get() = contact.accountID
    val name: String get() = contact.getSearchName()
}