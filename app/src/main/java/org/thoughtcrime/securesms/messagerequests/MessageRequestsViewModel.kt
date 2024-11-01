package org.thoughtcrime.securesms.messagerequests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.repository.ConversationRepository
import javax.inject.Inject

@HiltViewModel
class MessageRequestsViewModel @Inject constructor(
    private val repository: ConversationRepository
) : ViewModel() {

    // We assume thread.recipient is a contact or thread.invitingAdmin is not null
    fun blockMessageRequest(thread: ThreadRecord, blockRecipient: Recipient) = viewModelScope.launch {
        repository.setBlocked(thread.threadId, blockRecipient, true)
        deleteMessageRequest(thread)
    }

    fun deleteMessageRequest(thread: ThreadRecord) = viewModelScope.launch {
        repository.deleteMessageRequest(thread)
    }

    fun clearAllMessageRequests(block: Boolean) = viewModelScope.launch {
        repository.clearAllMessageRequests(block)
    }

}
