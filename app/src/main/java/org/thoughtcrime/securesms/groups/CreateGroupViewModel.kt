package org.thoughtcrime.securesms.groups

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.ThreadDatabase
import javax.inject.Inject

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val threadDb: ThreadDatabase,
    private val textSecurePreferences: TextSecurePreferences
) : ViewModel() {

    private val _members = MutableLiveData<List<String>>()
    val members: LiveData<List<String>> = _members

    init {
        viewModelScope.launch {
            threadDb.approvedConversationList.use { openCursor ->
                val reader = threadDb.readerFor(openCursor)
                val recipients = mutableListOf<Recipient>()
                while (true) {
                    recipients += reader.next?.recipient ?: break
                }
                withContext(Dispatchers.Main) {
                    _members.value = recipients
                        .filter { !it.isGroupRecipient && it.hasApprovedMe() && it.address.serialize() != textSecurePreferences.getLocalNumber() }
                        .map { it.address.serialize() }
                }
            }
        }
    }
}