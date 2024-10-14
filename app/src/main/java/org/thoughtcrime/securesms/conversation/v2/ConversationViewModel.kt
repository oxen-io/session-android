package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.goterl.lazysodium.utils.KeyPair
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.GroupMember
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.audio.AudioSlidePlayer
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.repository.ConversationRepository
import java.util.UUID

class ConversationViewModel(
    val threadId: Long,
    val edKeyPair: KeyPair?,
    private val repository: ConversationRepository,
    private val storage: StorageProtocol,
    private val messageDataProvider: MessageDataProvider,
    private val groupDb: GroupDatabase,
    private val threadDb: ThreadDatabase,
) : ViewModel() {

    val showSendAfterApprovalText: Boolean
        get() = recipient?.run { isContactRecipient && !isLocalNumber && !hasApprovedMe() } ?: false

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> get() = _uiState

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

    /**
     * The admin who invites us to this group(v2) conversation.
     *
     * null if this convo is not a group(v2) conversation, or error getting the info
     */
    val invitingAdmin: Recipient?
        get() {
            val recipient = recipient ?: return null
            if (!recipient.isClosedGroupV2Recipient) return null

            return repository.getInvitingAdmin(threadId)
        }

    private var _openGroup: RetrieveOnce<OpenGroup> = RetrieveOnce {
        storage.getOpenGroup(threadId)
    }
    val openGroup: OpenGroup?
        get() = _openGroup.value

    private val closedGroupMembers: List<GroupMember>
        get() {
            val recipient = recipient ?: return emptyList()
            if (!recipient.isClosedGroupV2Recipient) return emptyList()
            return storage.getMembers(recipient.address.serialize())
        }

    val isClosedGroupAdmin: Boolean
        get() {
            val recipient = recipient ?: return false
            return !recipient.isClosedGroupV2Recipient ||
                    (closedGroupMembers.firstOrNull { it.sessionId == storage.getUserPublicKey() }?.admin ?: false)
        }

    val serverCapabilities: List<String>
        get() = openGroup?.let { storage.getServerCapabilities(it.server) } ?: listOf()

    val blindedPublicKey: String?
        get() = if (openGroup == null || edKeyPair == null || !serverCapabilities.contains(OpenGroupApi.Capability.BLIND.name.lowercase())) null else {
            SodiumUtilities.blindedKeyPair(openGroup!!.publicKey, edKeyPair)?.publicKey?.asBytes
                ?.let { AccountId(IdPrefix.BLINDED, it) }?.hexString
        }

    val isMessageRequestThread : Boolean
        get() {
            val recipient = recipient ?: return false
            return !recipient.isLocalNumber && !recipient.isLegacyClosedGroupRecipient && !recipient.isCommunityRecipient && !recipient.isApproved
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
        viewModelScope.launch(Dispatchers.Default) {
            repository.recipientUpdateFlow(threadId)
                .collect { recipient ->
                    _uiState.update {
                        it.copy(
                            shouldExit = recipient == null,
                            showInput = shouldShowInput(recipient),
                            enableInputMediaControls = shouldEnableInputMediaControls(recipient),
                            messageRequestState = buildMessageRequestState(recipient),
                        )
                    }
                }
        }
    }

    /**
     * Determines if the input media controls should be enabled.
     *
     * Normally we will show the input media controls, only in these situations we hide them:
     *  1. First time we send message to a person.
     *     Since we haven't been approved by them, we can't send them any media, only text
     */
    private fun shouldEnableInputMediaControls(recipient: Recipient?): Boolean {
        if (recipient != null &&
            (recipient.is1on1 && !recipient.isLocalNumber) &&
            !recipient.hasApprovedMe()) {
            return false
        }

        return true
    }

    /**
     * Determines if the input bar should be shown.
     *
     * For these situations we hide the input bar:
     *  1. The user has been kicked from a group(v2), OR
     *  2. The legacy group is inactive, OR
     *  3. The community chat is read only
     */
    private fun shouldShowInput(recipient: Recipient?): Boolean {
        return when {
            recipient?.isClosedGroupV2Recipient == true -> !repository.isKicked(recipient)
            recipient?.isLegacyClosedGroupRecipient == true -> {
                groupDb.getGroup(recipient.address.toGroupString()).orNull()?.isActive == true
            }
            openGroup != null -> openGroup?.canWrite == true
            else -> true
        }
    }

    private fun buildMessageRequestState(recipient: Recipient?): MessageRequestUiState {
        // The basic requirement of showing a message request is:
        // 1. The other party has not been approved by us, AND
        // 2. We haven't sent a message to them before (if we do, we would be the one requesting permission), AND
        // 3. We have received message from them AND
        // 4. The type of conversation supports message request (only 1to1 and groups v2)

        if (
            recipient != null &&

            // Req 1: we haven't approved the other party
            (!recipient.isApproved && !recipient.isLocalNumber) &&

            // Req 4: the type of conversation supports message request
            (recipient.is1on1 || recipient.isClosedGroupV2Recipient) &&

            // Req 2: we haven't sent a message to them before
            !threadDb.getLastSeenAndHasSent(threadId).second() &&

            // Req 3: we have received message from them
            threadDb.getMessageCount(threadId) > 0
        ) {

            return MessageRequestUiState.Visible(
                acceptButtonText = if (recipient.isGroupRecipient) {
                    R.string.messageRequestGroupInviteDescription
                } else {
                    R.string.messageRequestsAcceptDescription
                },
                // You can block a 1to1 conversation, or a normal groups v2 conversation
                showBlockButton = recipient.is1on1 || recipient.isClosedGroupV2Recipient,
                declineButtonText = if (recipient.isClosedGroupV2Recipient) {
                    R.string.delete
                } else {
                    R.string.decline
                }
            )
        }

        return MessageRequestUiState.Invisible
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
        // inviting admin will be true if this request is a closed group message request
        val recipient = invitingAdmin ?: recipient ?: return Log.w("Loki", "Recipient was null for block action")
        if (recipient.isContactRecipient || recipient.isClosedGroupV2Recipient) {
            repository.setBlocked(threadId, recipient, true)
        }
    }

    fun unblock() {
        val recipient = recipient ?: return Log.w("Loki", "Recipient was null for unblock action")
        if (recipient.isContactRecipient) {
            repository.setBlocked(threadId, recipient, false)
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
        val currentState = _uiState.value.messageRequestState as? MessageRequestUiState.Visible
            ?: return@launch Log.w("Loki", "Current state was not visible for accept message request action")

        _uiState.update {
            it.copy(messageRequestState = MessageRequestUiState.Pending(currentState))
        }

        repository.acceptMessageRequest(threadId, recipient)
            .onSuccess {
                _uiState.update {
                    it.copy(messageRequestState = MessageRequestUiState.Invisible)
                }
            }
            .onFailure {
                showMessage("Couldn't accept message request due to error: $it")

                _uiState.update { state ->
                    state.copy(messageRequestState = currentState)
                }
            }
    }

    fun declineMessageRequest() = viewModelScope.launch {
        repository.declineMessageRequest(threadId, recipient!!)
            .onSuccess {
                _uiState.update { it.copy(shouldExit = true) }
            }
            .onFailure {
                showMessage("Couldn't decline message request due to error: $it")
            }
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

    /**
     * The input should be hidden when:
     * - We are in a community without write access
     * - We are dealing with a contact from a community (blinded recipient) that does not allow
     *   requests form community members
     */
    fun shouldHideInputBar(): Boolean = openGroup?.canWrite == false ||
            blindedRecipient?.blocksCommunityMessageRequests == true

    fun legacyBannerRecipient(context: Context): Recipient? = recipient?.run {
        storage.getLastLegacyRecipient(address.serialize())?.let { Recipient.from(context, Address.fromSerialized(it), false) }
    }

    fun onAttachmentDownloadRequest(attachment: DatabaseAttachment) {
        attachmentDownloadHandler.onAttachmentDownloadRequest(attachment)
    }

    fun beforeSendingTextOnlyMessage() {
        implicitlyApproveRecipient()
    }

    fun beforeSendingAttachments() {
        implicitlyApproveRecipient()
    }

    private fun implicitlyApproveRecipient() {
        val recipient = recipient

        if (uiState.value.messageRequestState is MessageRequestUiState.Visible) {
            acceptMessageRequest()
        } else if (recipient?.isApproved == false) {
            // edge case for new outgoing thread on new recipient without sending approval messages
            repository.setApproved(recipient, true)
        }
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
        private val storage: StorageProtocol,
        private val messageDataProvider: MessageDataProvider,
        private val groupDb: GroupDatabase,
        private val threadDb: ThreadDatabase,
        @ApplicationContext
        private val context: Context,
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConversationViewModel(
                threadId = threadId,
                edKeyPair = edKeyPair,
                repository = repository,
                storage = storage,
                messageDataProvider = messageDataProvider,
                groupDb = groupDb,
                threadDb = threadDb,
            ) as T
        }
    }
}

data class UiMessage(val id: Long, val message: String)

data class ConversationUiState(
    val uiMessages: List<UiMessage> = emptyList(),
    val messageRequestState: MessageRequestUiState = MessageRequestUiState.Invisible,
    val shouldExit: Boolean = false,
    val showInput: Boolean = true,
    val enableInputMediaControls: Boolean = true,
)

sealed interface MessageRequestUiState {
    data object Invisible : MessageRequestUiState

    data class Pending(val prevState: Visible) : MessageRequestUiState

    data class Visible(
        @StringRes val acceptButtonText: Int,
        val showBlockButton: Boolean,
        @StringRes val declineButtonText: Int,
    ) : MessageRequestUiState
}

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
