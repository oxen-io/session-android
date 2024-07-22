package org.thoughtcrime.securesms.conversation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.libsession_util.util.GroupDisplayInfo
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.LibSessionGroupLeavingJob
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.AccountId

class ConversationSettingsViewModel(
    val threadId: Long,
    private val storage: StorageProtocol,
    private val prefs: TextSecurePreferences
): ViewModel() {

    val recipient get() = storage.getRecipientForThread(threadId)

    fun isPinned() = storage.isPinned(threadId)

    fun togglePin() = viewModelScope.launch {
        val isPinned = storage.isPinned(threadId)
        storage.setPinned(threadId, !isPinned)
    }

    fun autoDownloadAttachments() = recipient?.let { recipient -> storage.shouldAutoDownloadAttachments(recipient) } ?: false

    fun setAutoDownloadAttachments(shouldDownload: Boolean) {
        recipient?.let { recipient -> storage.setAutoDownloadAttachments(recipient, shouldDownload) }
    }

    fun isUserGroupAdmin(): Boolean = recipient?.let { recipient ->
        when {
            recipient.isLegacyClosedGroupRecipient -> {
                val localUserAddress = prefs.getLocalNumber() ?: return@let false
                val group = storage.getGroup(recipient.address.toGroupString())
                group?.admins?.contains(Address.fromSerialized(localUserAddress)) ?: false // this will have to be replaced for new closed groups
            }
            recipient.isClosedGroupV2Recipient -> {
                val group = storage.getLibSessionClosedGroup(recipient.address.serialize()) ?: return@let false
                group.adminKey != null
            }
            else -> false
        }
    } ?: false

    fun clearMessages(forAll: Boolean) {
        if (forAll && !isUserGroupAdmin()) return

        if (!forAll) {
            viewModelScope.launch {
                storage.clearMessages(threadId)
            }
        } else {
            // do a send message here and on success do a clear messages
            viewModelScope.launch {
                storage.clearMessages(threadId)
            }
        }
    }

    fun closedGroupInfo(): GroupDisplayInfo? = recipient
        ?.address
        ?.takeIf { it.isClosedGroupV2 }
        ?.serialize()
        ?.let(storage::getClosedGroupDisplayInfo)

    // Assume that user has verified they don't want to add a new admin etc
    suspend fun leaveGroup() {
        val recipient = recipient ?: return
        return withContext(Dispatchers.IO) {
            val groupLeave = LibSessionGroupLeavingJob(
                AccountId(recipient.address.serialize()),
                true
            )
            JobQueue.shared.add(groupLeave)
        }
    }

    // DI-related
    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(threadId: Long): Factory
    }
    class Factory @AssistedInject constructor(
        @Assisted private val threadId: Long,
        private val storage: StorageProtocol,
        private val prefs: TextSecurePreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConversationSettingsViewModel(threadId, storage, prefs) as T
        }
    }

}