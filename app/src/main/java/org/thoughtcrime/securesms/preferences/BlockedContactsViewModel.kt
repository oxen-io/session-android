package org.thoughtcrime.securesms.preferences

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.copper.flow.observeQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
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
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
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

    fun unblock(context: Context) {
        storage.unblock(state.selectedItems)
        _state.value = state.copy(selectedItems = emptySet())
        // TODO: Remove in UserConfig branch
        GlobalScope.launch(Dispatchers.IO) {
            ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(context)
        }
    }

    fun select(selectedItem: Recipient, isSelected: Boolean) {
        _state.value = state.run {
            if (isSelected) copy(selectedItems = selectedItems + selectedItem)
            else copy(selectedItems = selectedItems - selectedItem)
        }
    }

    fun getTitle(context: Context): String =
        if (state.selectedItems.size == 1) {
            context.getString(R.string.Unblock_dialog__title_single, state.selectedItems.first().name)
        } else {
            context.getString(R.string.Unblock_dialog__title_multiple)
        }

    fun getMessage(context: Context): String {
        if (state.selectedItems.size == 1) {
            return context.getString(R.string.Unblock_dialog__message, state.selectedItems.first().name)
        }
        val stringBuilder = StringBuilder()
        val iterator = state.selectedItems.iterator()
        var numberAdded = 0
        while (iterator.hasNext() && numberAdded < 3) {
            val nextRecipient = iterator.next()
            if (numberAdded > 0) stringBuilder.append(", ")

            stringBuilder.append(nextRecipient.name)
            numberAdded++
        }
        val overflow = state.selectedItems.size - numberAdded
        if (overflow > 0) {
            stringBuilder.append(" ")
            val string = context.resources.getQuantityString(R.plurals.Unblock_dialog__message_multiple_overflow, overflow)
            stringBuilder.append(string.format(overflow))
        }
       return context.getString(R.string.Unblock_dialog__message, stringBuilder.toString())
    }

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
