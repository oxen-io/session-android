package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.goterl.lazysodium.utils.KeyPair
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.messaging.utilities.SessionId
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.database.MmsDatabase
import org.thoughtcrime.securesms.audio.AudioSlidePlayer
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.repository.ConversationRepository
import java.util.UUID

class ConversationViewModel(
    val threadId: Long,
    val edKeyPair: KeyPair?,
    private val repository: ConversationRepository,
    private val storage: Storage,
    private val messageDataProvider: MessageDataProvider,
    database: MmsDatabase,
) : ViewModel() {

    val showSendAfterApprovalText: Boolean
        get() = recipient?.run { isContactRecipient && !isLocalNumber && !hasApprovedMe() } ?: false

    private val _uiState = MutableStateFlow(ConversationUiState(conversationExists = true))
    val uiState: StateFlow<ConversationUiState> = _uiState

    private var _recipient: RetrieveOnce<Recipient> = RetrieveOnce {
        repository.maybeGetRecipientForThreadId(threadId)
    }
    val expirationConfiguration: ExpirationConfiguration?
        get() = storage.getExpirationConfiguration(threadId)

    val recipient: Recipient?
        get() = _recipient.value

    val blindedRecipient: Recipient?
        get() = _recipient.value?.let { recipient ->
            when {
                recipient.isOpenGroupOutboxRecipient -> recipient
                recipient.isOpenGroupInboxRecipient -> repository.maybeGetBlindedRecipient(recipient)
                else -> null
            }
        }

    private var _openGroup: RetrieveOnce<OpenGroup> = RetrieveOnce {
        storage.getOpenGroup(threadId)
    }
    val openGroup: OpenGroup?
        get() = _openGroup.value

    val serverCapabilities: List<String>
        get() = openGroup?.let { storage.getServerCapabilities(it.server) } ?: listOf()

    val blindedPublicKey: String?
        get() = if (openGroup == null || edKeyPair == null || !serverCapabilities.contains(OpenGroupApi.Capability.BLIND.name.lowercase())) null else {
            SodiumUtilities.blindedKeyPair(openGroup!!.publicKey, edKeyPair)?.publicKey?.asBytes
                ?.let { SessionId(IdPrefix.BLINDED, it) }?.hexString
        }

    val isMessageRequestThread : Boolean
        get() {
            val recipient = recipient ?: return false
            return !recipient.isLocalNumber && !recipient.isGroupRecipient && !recipient.isApproved
        }

    val canReactToMessages: Boolean
        // allow reactions if the open group is null (normal conversations) or the open group's capabilities include reactions
        get() = (openGroup == null || OpenGroupApi.Capability.REACTIONS.name.lowercase() in serverCapabilities)

    private val attachmentDownloadHandler = AttachmentDownloadHandler(
        storage = storage,
        messageDataProvider = messageDataProvider,
        scope = viewModelScope,
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.recipientUpdateFlow(threadId)
                .collect { recipient ->
                    if (recipient == null && _uiState.value.conversationExists) {
                        _uiState.update { it.copy(conversationExists = false) }
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()

        // Stop all voice message when exiting this page
        AudioSlidePlayer.stopAll()
    }

    fun saveDraft(text: String) {
        GlobalScope.launch(Dispatchers.IO) {
            repository.saveDraft(threadId, text)
        }
    }

    fun getDraft(): String? {
        val draft: String? = repository.getDraft(threadId)

        viewModelScope.launch(Dispatchers.IO) {
            repository.clearDrafts(threadId)
        }

        return draft
    }

    fun inviteContacts(contacts: List<Recipient>) {
        repository.inviteContacts(threadId, contacts)
    }

    fun block() {
        val recipient = recipient ?: return Log.w("Loki", "Recipient was null for block action")
        if (recipient.isContactRecipient) {
            repository.setBlocked(recipient, true)
        }
    }

    fun unblock() {
        val recipient = recipient ?: return Log.w("Loki", "Recipient was null for unblock action")
        if (recipient.isContactRecipient) {
            repository.setBlocked(recipient, false)
        }
    }

    fun deleteThread() = viewModelScope.launch {
        repository.deleteThread(threadId)
    }

    fun deleteLocally(message: MessageRecord) {
        stopPlayingAudioMessage(message)
        val recipient = recipient ?: return Log.w("Loki", "Recipient was null for delete locally action")
        repository.deleteLocally(recipient, message)
    }

    /**
     * Stops audio player if its current playing is the one given in the message.
     */
    private fun stopPlayingAudioMessage(message: MessageRecord) {
        val mmsMessage = message as? MmsMessageRecord ?: return
        val audioSlide = mmsMessage.slideDeck.audioSlide ?: return
        AudioSlidePlayer.getInstance()?.takeIf { it.audioSlide == audioSlide }?.stop()
    }

    fun setRecipientApproved() {
        val recipient = recipient ?: return Log.w("Loki", "Recipient was null for set approved action")
        repository.setApproved(recipient, true)
    }

    fun deleteForEveryone(message: MessageRecord) = viewModelScope.launch {
        val recipient = recipient ?: return@launch Log.w("Loki", "Recipient was null for delete for everyone - aborting delete operation.")
        stopPlayingAudioMessage(message)

        repository.deleteForEveryone(threadId, recipient, message)
            .onSuccess {
                Log.d("Loki", "Deleted message ${message.id} ")
                stopPlayingAudioMessage(message)
            }
            .onFailure {
                Log.w("Loki", "FAILED TO delete message ${message.id} ")
                showMessage("Couldn't delete message due to error: $it")
            }
    }

    fun deleteMessagesWithoutUnsendRequest(messages: Set<MessageRecord>) = viewModelScope.launch {
        repository.deleteMessageWithoutUnsendRequest(threadId, messages)
            .onFailure {
                showMessage("Couldn't delete message due to error: $it")
            }
    }

    fun banUser(recipient: Recipient) = viewModelScope.launch {
        repository.banUser(threadId, recipient)
            .onSuccess {
                showMessage("Successfully banned user")
            }
            .onFailure {
                showMessage("Couldn't ban user due to error: $it")
            }
    }

    fun banAndDeleteAll(messageRecord: MessageRecord) = viewModelScope.launch {

        repository.banAndDeleteAll(threadId, messageRecord.individualRecipient)
            .onSuccess {
                // At this point the server side messages have been successfully deleted..
                showMessage("Successfully banned user and deleted all their messages")

                // ..so we can now delete all their messages in this thread from local storage & remove the views.
                repository.deleteAllLocalMessagesInThreadFromSenderOfMessage(messageRecord)
            }
            .onFailure {
                showMessage("Couldn't execute request due to error: $it")
            }
    }

    fun acceptMessageRequest() = viewModelScope.launch {
        val recipient = recipient ?: return@launch Log.w("Loki", "Recipient was null for accept message request action")
        repository.acceptMessageRequest(threadId, recipient)
            .onSuccess {
                _uiState.update {
                    it.copy(isMessageRequestAccepted = true)
                }
            }
            .onFailure {
                showMessage("Couldn't accept message request due to error: $it")
            }
    }

    fun declineMessageRequest() {
        repository.declineMessageRequest(threadId)
    }

    private fun showMessage(message: String) {
        _uiState.update { currentUiState ->
            val messages = currentUiState.uiMessages + UiMessage(
                id = UUID.randomUUID().mostSignificantBits,
                message = message
            )
            currentUiState.copy(uiMessages = messages)
        }
    }

    fun messageShown(messageId: Long) {
        _uiState.update { currentUiState ->
            val messages = currentUiState.uiMessages.filterNot { it.id == messageId }
            currentUiState.copy(uiMessages = messages)
        }
    }

    fun hasReceived(): Boolean {
        return repository.hasReceived(threadId)
    }

    fun updateRecipient() {
        _recipient.updateTo(repository.maybeGetRecipientForThreadId(threadId))
    }

    fun hidesInputBar(): Boolean = openGroup?.canWrite != true &&
        blindedRecipient?.blocksCommunityMessageRequests == true

    fun legacyBannerRecipient(context: Context): Recipient? = recipient?.run {
        storage.getLastLegacyRecipient(address.serialize())?.let { Recipient.from(context, Address.fromSerialized(it), false) }
    }

    fun onAttachmentDownloadRequest(attachment: DatabaseAttachment) {
        attachmentDownloadHandler.onAttachmentDownloadRequest(attachment)
    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(threadId: Long, edKeyPair: KeyPair?): Factory
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @AssistedInject constructor(
        @Assisted private val threadId: Long,
        @Assisted private val edKeyPair: KeyPair?,
        private val repository: ConversationRepository,
        private val storage: Storage,
        private val mmsDatabase: MmsDatabase,
        private val messageDataProvider: MessageDataProvider,
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConversationViewModel(
                threadId = threadId,
                edKeyPair = edKeyPair,
                repository = repository,
                storage = storage,
                messageDataProvider = messageDataProvider,
                database = mmsDatabase
            ) as T
        }
    }
}

data class UiMessage(val id: Long, val message: String)

data class ConversationUiState(
    val uiMessages: List<UiMessage> = emptyList(),
    val isMessageRequestAccepted: Boolean? = null,
    val conversationExists: Boolean
)

data class RetrieveOnce<T>(val retrieval: () -> T?) {
    private var triedToRetrieve: Boolean = false
    private var _value: T? = null

    val value: T?
        get() {
            if (triedToRetrieve) { return _value }

            triedToRetrieve = true
            _value = retrieval()
            return _value
        }

    fun updateTo(value: T?) { _value = value }
}
