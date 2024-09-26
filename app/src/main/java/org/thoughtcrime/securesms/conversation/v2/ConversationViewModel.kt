package org.thoughtcrime.securesms.conversation.v2

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.goterl.lazysodium.utils.KeyPair
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.messaging.utilities.AccountId
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.Address.Companion.fromSerialized
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.audio.AudioSlidePlayer
import org.thoughtcrime.securesms.database.LokiMessageDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.mms.AudioSlide
import org.thoughtcrime.securesms.repository.ConversationRepository
import java.util.UUID

class ConversationViewModel(
    val threadId: Long,
    val edKeyPair: KeyPair?,
    private val application: Application,
    private val repository: ConversationRepository,
    private val storage: Storage,
    private val messageDataProvider: MessageDataProvider,
    private val lokiMessageDb: LokiMessageDatabase,
    private val textSecurePreferences: TextSecurePreferences
) : ViewModel() {

    val showSendAfterApprovalText: Boolean
        get() = recipient?.run { isContactRecipient && !isLocalNumber && !hasApprovedMe() } ?: false

    private val _uiState = MutableStateFlow(ConversationUiState(conversationExists = true))
    val uiState: StateFlow<ConversationUiState> = _uiState

    private val _dialogsState = MutableStateFlow(DialogsState())
    val dialogsState: StateFlow<DialogsState> = _dialogsState

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

    private var communityWriteAccessJob: Job? = null

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
                ?.let { AccountId(IdPrefix.BLINDED, it) }?.hexString
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

        // listen to community write access updates from this point
        communityWriteAccessJob?.cancel()
        communityWriteAccessJob = viewModelScope.launch {
            OpenGroupManager.getCommunitiesWriteAccessFlow()
                .map {
                    if(openGroup?.groupId != null)
                        it[openGroup?.groupId]
                    else null
                }
                .filterNotNull()
                .collect{
                    // update our community object
                    _openGroup.updateTo(openGroup?.copy(canWrite = it))
                    // when we get an update on the write access of a community
                    // we need to update the input text accordingly
                    _uiState.update { state ->
                        state.copy(hideInputBar = shouldHideInputBar())
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

    fun handleMessagesDeletion(messages: Set<MessageRecord>){
        val conversation = recipient
        if (conversation == null) {
            Log.w("ConversationActivityV2", "Asked to delete messages but could not obtain viewModel recipient - aborting.")
            return
        }

        // Refer to our figma document for info on message deletion [https://www.figma.com/design/kau6LggVcMMWmZRMibEo8F/Standardise-Message-Deletion?node-id=0-1&t=dEPcU0SZ9G2s4gh2-0]

        //todo DELETION delete for everyone

        //todo DELETION delete all my devices

        //todo DELETION handle control messages deletion ( and make clickable )

        //todo DELETION handle multi select scenarios

        //todo DELETION check that the unread status works as expected when deleting a message

        //todo DELETION handle errors: Toasts for errors, or deleting messages not fully sent yet

        viewModelScope.launch(Dispatchers.IO) {
            val allSentByCurrentUser = messages.all { it.isOutgoing }
            // hashes are required if wanting to delete messages from the 'storage server' - they are not required for communities
            val canDeleteForEveryone = conversation.isCommunityRecipient || messages.all {
                lokiMessageDb.getMessageServerHash(
                    it.id,
                    it.isMms
                ) != null
            }
            // Determining is the current user is an admin will depend on the kind of conversation we are in
            val isAdmin = when {
                //todo GROUPS V2 add logic where code is commented to determine if user is an admin - CAREFUL in the current old code:
                // isClosedGroup refers to the existing legacy groups.
                // With the groupsV2 changes, isClosedGroup refers to groupsV2 and isLegacyClosedGroup is a new property to refer to old groups

                // for Groups V2
                // conversation: check if it is a GroupsV2 conversation - then check if user is an admin

                // for legacy groups, check if the user created the group
                conversation.isClosedGroupRecipient -> { //todo GROUPS V2 this property will change for groups v2. Check for legacyGroup here
                    // for legacy groups, we check if the current user is the one who created the group
                    run {
                        val localUserAddress =
                            textSecurePreferences.getLocalNumber() ?: return@run false
                        val group = storage.getGroup(conversation.address.toGroupString())
                        group?.admins?.contains(fromSerialized(localUserAddress)) ?: false
                    }
                }

                // for communities the the `isUserModerator` field
                conversation.isCommunityRecipient -> isUserCommunityManager()

                // false in other cases
                else -> false
            }


            // There are three types of dialogs for deletion:
            // 1- Delete on device only OR all devices - Used for Note to self
            // 2- Delete on device only OR for everyone - Used for 'admins' or a user's own messages, as long as the message have a server hash
            // 3- Delete on device only - Used otherwise
            when {
                // the conversation is a note to self
                conversation.isLocalNumber -> {
                    _dialogsState.update {
                        it.copy(deleteAllDevices = messages)
                    }
                }

                // If the user is an admin or is interacting with their own message And are allowed to delete for everyone
                (isAdmin || allSentByCurrentUser) && canDeleteForEveryone -> {
                    _dialogsState.update {
                        it.copy(
                            deleteEveryone = DeleteForEveryoneDialogData(
                                messages = messages,
                                defaultToEveryone = isAdmin
                            )
                        )
                    }
                }

                // for non admins, users interacting with someone else's message, or control messages
                else -> {
                    //todo DELETION this should also happen for ControlMessages
                    _dialogsState.update {
                        it.copy(deleteDeviceOnly = messages)
                    }
                }
            }
        }
    }

    private fun isUserCommunityManager() = openGroup?.let { openGroup ->
        val userPublicKey = textSecurePreferences.getLocalNumber() ?: return@let false
        OpenGroupManager.isUserModerator(application, openGroup.id, userPublicKey, blindedPublicKey)
    } ?: false

    /**
     * This will delete these messages from the db
     * Not to be confused with 'marking messages as deleted'
     */
    fun deleteMessages(messages: Set<MessageRecord>, threadId: Long) {
        repository.deleteMessages(messages, threadId)
    }

    /**
     * This will mark the messages as deleted, locally only.
     * Attachments and other related data will be removed from the db,
     * but the messages themselves won't be removed from the db.
     * Instead they will appear as a special type of message
     * that says something like "This message was deleted"
     */
    private fun markAsDeletedLocally(messages: Set<MessageRecord>) {
        // make sure to stop audio messages, if any
        messages.filterIsInstance<MmsMessageRecord>()
            .mapNotNull { it.slideDeck.audioSlide }
            .forEach(::stopMessageAudio)


        repository.markAsDeletedLocally(
            messages = messages,
            displayedMessage = application.getString(R.string.deleteMessageDeletedLocally)
        )

        // show confirmation toast
        Toast.makeText(
            application,
            application.resources.getQuantityString(R.plurals.deleteMessageDeleted, messages.count(), messages.count()),
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Stops audio player if its current playing is the one given in the message.
     */
    private fun stopMessageAudio(message: MessageRecord) {
        val mmsMessage = message as? MmsMessageRecord ?: return
        val audioSlide = mmsMessage.slideDeck.audioSlide ?: return
        stopMessageAudio(audioSlide)
    }
    private fun stopMessageAudio(audioSlide: AudioSlide) {
        AudioSlidePlayer.getInstance()?.takeIf { it.audioSlide == audioSlide }?.stop()
    }

    fun setRecipientApproved() {
        val recipient = recipient ?: return Log.w("Loki", "Recipient was null for set approved action")
        repository.setApproved(recipient, true)
    }

    /**
     * This will mark the messages as deleted, for everyone.
     * Attachments and other related data will be removed from the db,
     * but the messages themselves won't be removed from the db.
     * Instead they will appear as a special type of message
     * that says something like "This message was deleted"
     */
    private fun markAsDeletedForEveryone(message: MessageRecord) = viewModelScope.launch {
        val recipient = recipient ?: return@launch Log.w("Loki", "Recipient was null for delete for everyone - aborting delete operation.")
        stopMessageAudio(message)

        repository.markAsDeletedForEveryone(threadId, recipient, message)
            .onSuccess {
                Log.d("Loki", "Deleted message ${message.id} ")
            }
            .onFailure {
                Log.w("Loki", "FAILED TO delete message ${message.id} ")
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

    fun onCommand(command: Commands) {
        when (command) {
            is Commands.ShowOpenUrlDialog -> {
                _dialogsState.update {
                    it.copy(openLinkDialogUrl = command.url)
                }
            }

            is Commands.HideDeleteDeviceOnlyDialog -> {
                _dialogsState.update {
                    it.copy(deleteDeviceOnly = null)
                }
            }

            is Commands.HideDeleteEveryoneDialog -> {
                _dialogsState.update {
                    it.copy(deleteEveryone = null)
                }
            }

            is Commands.HideDeleteAllDevicesDialog -> {
                _dialogsState.update {
                    it.copy(deleteAllDevices = null)
                }
            }

            is Commands.MarkAsDeletedLocally -> {
                // hide dialog first
                _dialogsState.update {
                    it.copy(deleteDeviceOnly = null)
                }

                markAsDeletedLocally(command.messages)
            }
            is Commands.MarkAsDeletedForEveryone -> {
                //todo DELETION mark as deleted for everyone here
                //markAsDeletedForEveryone(command.messages)
            }
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
        private val application: Application,
        private val repository: ConversationRepository,
        private val storage: Storage,
        private val messageDataProvider: MessageDataProvider,
        private val lokiMessageDb: LokiMessageDatabase,
        private val textSecurePreferences: TextSecurePreferences
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConversationViewModel(
                threadId = threadId,
                edKeyPair = edKeyPair,
                application = application,
                repository = repository,
                storage = storage,
                messageDataProvider = messageDataProvider,
                lokiMessageDb = lokiMessageDb,
                textSecurePreferences = textSecurePreferences
            ) as T
        }
    }

    data class DialogsState(
        val openLinkDialogUrl: String? = null,
        val deleteDeviceOnly: Set<MessageRecord>? = null,
        val deleteEveryone: DeleteForEveryoneDialogData? = null,
        val deleteAllDevices: Set<MessageRecord>? = null,
    )

    data class DeleteForEveryoneDialogData(
        val messages: Set<MessageRecord>,
        val defaultToEveryone: Boolean
    )

    sealed class Commands {
        data class ShowOpenUrlDialog(val url: String?) : Commands()
        data object HideDeleteDeviceOnlyDialog : Commands()
        data object HideDeleteEveryoneDialog : Commands()
        data object HideDeleteAllDevicesDialog : Commands()

        data class MarkAsDeletedLocally(val messages: Set<MessageRecord>): Commands()
        data class MarkAsDeletedForEveryone(val messages: Set<MessageRecord>): Commands()
    }
}

data class UiMessage(val id: Long, val message: String)

data class ConversationUiState(
    val uiMessages: List<UiMessage> = emptyList(),
    val isMessageRequestAccepted: Boolean? = null,
    val conversationExists: Boolean,
    val hideInputBar: Boolean = false
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
