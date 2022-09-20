package org.thoughtcrime.securesms.preferences

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.Storage
import javax.inject.Inject

@HiltViewModel
class BlockedContactsViewModel @Inject constructor(private val storage: Storage): ViewModel() {

    private val _state = MutableStateFlow(BlockedContactsViewState(emptyList()))

    val state: SharedFlow<BlockedContactsViewState>
        get() = _state.asSharedFlow()

    init {

    }

    fun unblock(toUnblock: List<Recipient>) {
        storage.unblock(toUnblock)
    }

    data class BlockedContactsViewState(
        val blockedContacts: List<Recipient>
    )

}