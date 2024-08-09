package org.thoughtcrime.securesms.preferences

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.copper.flow.observeQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.DatabaseContentProviders
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.util.adapter.SelectableItem
import javax.inject.Inject

@HiltViewModel
class BlockedContactsViewModel @Inject constructor(private val storage: Storage): ViewModel() {

    private val executor = viewModelScope + SupervisorJob()

    private val listUpdateChannel = Channel<Unit>(capacity = Channel.CONFLATED)

    private val _state = MutableLiveData(BlockedContactsViewState())

    val state get() = _state.value!!

    fun subscribe(context: Context): LiveData<BlockedContactsViewState> {
        executor.launch(IO) {
            context.contentResolver
                .observeQuery(DatabaseContentProviders.Recipient.CONTENT_URI)
                .onStart {
                    listUpdateChannel.trySend(Unit)
                }
                .onEach {
                    listUpdateChannel.trySend(Unit)
                }
                .collect()
        }
        executor.launch(IO) {
            for (update in listUpdateChannel) {
                val blockedContactState = state.copy(
                    blockedContacts = storage.blockedContacts().sortedBy { it.name }
                )
                withContext(Main) {
                    _state.value = blockedContactState
                }
            }
        }
        return _state
    }

    fun unblock() {
        storage.setBlocked(state.selectedItems, false)
        _state.value = state.copy(selectedItems = emptySet())
    }

    fun select(selectedItem: Recipient, isSelected: Boolean) {
        _state.value = state.run {
            if (isSelected) copy(selectedItems = selectedItems + selectedItem)
            else copy(selectedItems = selectedItems - selectedItem)
        }
    }

    fun getTitle(context: Context): String = context.getString(R.string.blockUnblock)

    fun getMessage(context: Context): String = context.getString(R.string.blockUnblock)

    fun toggle(selectable: SelectableItem<Recipient>) {
        _state.value = state.run {
            if (selectable.item in selectedItems) copy(selectedItems = selectedItems - selectable.item)
            else copy(selectedItems = selectedItems + selectable.item)
        }
    }

    data class BlockedContactsViewState(
        val blockedContacts: List<Recipient> = emptyList(),
        val selectedItems: Set<Recipient> = emptySet()
    ) {
        val items = blockedContacts.map { SelectableItem(it, it in selectedItems) }

        val unblockButtonEnabled get() = selectedItems.isNotEmpty()
        val emptyStateMessageTextViewVisible get() = blockedContacts.isEmpty()
        val nonEmptyStateGroupVisible get() = blockedContacts.isNotEmpty()
    }
}
