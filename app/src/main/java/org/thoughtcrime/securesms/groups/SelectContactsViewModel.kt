package org.thoughtcrime.securesms.groups

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.truncateIdForDisplay
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.home.search.getSearchName

@OptIn(FlowPreview::class)
@HiltViewModel(assistedFactory = SelectContactsViewModel.Factory::class)
class SelectContactsViewModel @AssistedInject constructor(
    private val storage: StorageProtocol,
    private val configFactory: ConfigFactory,
    @ApplicationContext private val appContext: Context,
    @Assisted private val excludingAccountIDs: Set<AccountId>,
    @Assisted private val scope: CoroutineScope
) : ViewModel() {
    // Input: The search query
    private val mutableSearchQuery = MutableStateFlow("")

    // Input: The selected contact account IDs
    private val mutableSelectedContactAccountIDs = MutableStateFlow(emptySet<AccountId>())

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
    val currentSelected: Set<AccountId>
        get() = contacts.value
            .asSequence()
            .filter { it.selected }
            .map { it.accountID }
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
                val allContacts = configFactory.withUserConfigs {
                    it.contacts.all().filter { it.approvedMe }
                }

                if (excludingAccountIDs.isEmpty()) {
                    allContacts
                } else {
                    allContacts.filterNot { AccountId(it.id) in excludingAccountIDs }
                }.map { Recipient.from(appContext, Address.fromSerialized(it.id), false) }
            }
        }


    private fun filterContacts(
        contacts: Collection<Recipient>,
        query: String,
        selectedAccountIDs: Set<AccountId>
    ): List<ContactItem> {
        return contacts
            .asSequence()
            .filter { query.isBlank() || it.getSearchName().contains(query, ignoreCase = true) }
            .map { contact ->
                val accountId = AccountId(contact.address.serialize())
                ContactItem(
                    name = contact.getSearchName(),
                    accountID = accountId,
                    selected = selectedAccountIDs.contains(accountId),
                )
            }
            .toList()
    }

    fun onSearchQueryChanged(query: String) {
        mutableSearchQuery.value = query
    }

    fun onContactItemClicked(accountID: AccountId) {
        val newSet = mutableSelectedContactAccountIDs.value.toHashSet()
        if (!newSet.remove(accountID)) {
            newSet.add(accountID)
        }
        mutableSelectedContactAccountIDs.value = newSet
    }

    @AssistedFactory
    interface Factory {
        fun create(
            excludingAccountIDs: Set<AccountId> = emptySet(),
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        ): SelectContactsViewModel
    }
}

data class ContactItem(
    val accountID: AccountId,
    val name: String,
    val selected: Boolean,
)
