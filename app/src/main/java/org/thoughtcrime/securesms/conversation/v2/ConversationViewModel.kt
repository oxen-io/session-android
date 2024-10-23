package org.thoughtcrime.securesms.conversation.v2

import android.app.Application
import android.content.Context
import android.view.MenuItem
import androidx.annotation.StringRes
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.goterl.lazysodium.utils.KeyPair
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.GroupMember
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.groups.GroupManagerV2
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.Address.Companion.fromSerialized
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.recipients.MessageType
import org.session.libsession.utilities.recipients.getType
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.audio.AudioSlidePlayer
import org.thoughtcrime.securesms.conversation.v2.menus.ConversationMenuHelper
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.LokiMessageDatabase
import org.thoughtcrime.securesms.database.ReactionDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.mms.AudioSlide
import org.thoughtcrime.securesms.repository.ConversationRepository
import java.util.UUID

class ConversationViewModel(
    val threadId: Long,
    val edKeyPair: KeyPair?,
    private val application: Application,
    private val repository: ConversationRepository,
    private val storage: StorageProtocol,
    private val messageDataProvider: MessageDataProvider,
    private val groupDb: GroupDatabase,
    private val threadDb: ThreadDatabase,
    private val reactionDb: ReactionDatabase,
    private val lokiMessageDb: LokiMessageDatabase,
    private val textSecurePreferences: TextSecurePreferences,
    private val configFactory: ConfigFactory,
    private val groupManagerV2: GroupManagerV2,
) : ViewModel() {

    val showSendAfterApprovalText: Boolean
        get() = recipient?.run { isContactRecipient && !isLocalNumber && !hasApprovedMe() } ?: false

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> get() = _uiState

    private val _dialogsState = MutableStateFlow(DialogsState())
    val dialogsState: StateFlow<DialogsState> = _dialogsState

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    private var _recipient: RetrieveOnce<Recipient> = RetrieveOnce {
        val conversation = repository.maybeGetRecipientForThreadId(threadId)

        // set admin from current conversation
        val conversationType = conversation?.getType()
        // Determining is the current user is an admin will depend on the kind of conversation we are in
        _isAdmin.value = when(conversationType) {
        // for Groups V2
        MessageType.GROUPS_V2 -> {
            //todo GROUPS V2 add logic where code is commented to determine if user is an admin
            false // FANCHAO - properly set up admin for groups v2 here
        }

        // for legacy groups, check if the user created the group
        MessageType.LEGACY_GROUP -> {
            // for legacy groups, we check if the current user is the one who created the group
            run {
                val localUserAddress =
                    textSecurePreferences.getLocalNumber() ?: return@run false
                val group = storage.getGroup(conversation.address.toGroupString())
                group?.admins?.contains(fromSerialized(localUserAddress)) ?: false
            }
        }

        // for communities the the `isUserModerator` field
        MessageType.COMMUNITY -> isUserCommunityManager()

        // false in other cases
        else -> false
    }

        conversation
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
            recipient?.isClosedGroupV2Recipient == true -> !repository.isGroupReadOnly(recipient)
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

    fun handleMessagesDeletion(messages: Set<MessageRecord>){
        val conversation = recipient
        if (conversation == null) {
            Log.w("ConversationActivityV2", "Asked to delete messages but could not obtain viewModel recipient - aborting.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val allSentByCurrentUser = messages.all { it.isOutgoing }

            val conversationType = conversation.getType()

            // hashes are required if wanting to delete messages from the 'storage server'
            // They are not required for communities OR if all messages are outgoing
            // also we can only delete deleted messages and control messages (marked as deleted) locally
            val canDeleteForEveryone = messages.all{ !it.isDeleted && !it.isControlMessage } && (
                    messages.all { it.isOutgoing } ||
                    conversationType == MessageType.COMMUNITY ||
                            messages.all { lokiMessageDb.getMessageServerHash(it.id, it.isMms) != null }
                    )

            // There are three types of dialogs for deletion:
            // 1- Delete on device only OR all devices - Used for Note to self
            // 2- Delete on device only OR for everyone - Used for 'admins' or a user's own messages, as long as the message have a server hash
            // 3- Delete on device only - Used otherwise
            when {
                // the conversation is a note to self
                conversationType == MessageType.NOTE_TO_SELF -> {
                    _dialogsState.update {
                        it.copy(deleteEveryone = DeleteForEveryoneDialogData(
                                messages = messages,
                                defaultToEveryone = false,
                                everyoneEnabled = canDeleteForEveryone,
                                messageType = conversationType,
                                deleteForEveryoneLabel = application.getString(R.string.deleteMessageDevicesAll),
                                warning = if(canDeleteForEveryone) null else
                                    application.resources.getQuantityString(
                                        R.plurals.deleteMessageNoteToSelfWarning, messages.count(), messages.count()
                                    )
                            )
                        )
                    }
                }

                // If the user is an admin or is interacting with their own message And are allowed to delete for everyone
                (isAdmin.value || allSentByCurrentUser) && canDeleteForEveryone -> {
                    _dialogsState.update {
                        it.copy(
                            deleteEveryone = DeleteForEveryoneDialogData(
                                messages = messages,
                                defaultToEveryone = isAdmin.value,
                                everyoneEnabled = true,
                                deleteForEveryoneLabel = application.getString(R.string.deleteMessageEveryone),
                                messageType = conversationType
                            )
                        )
                    }
                }

                // for non admins, users interacting with someone else's message, or control messages
                else -> {
                    _dialogsState.update {
                        it.copy(
                            deleteEveryone = DeleteForEveryoneDialogData(
                                messages = messages,
                                defaultToEveryone = false,
                                everyoneEnabled = false, // disable 'delete for everyone' - can only delete locally in this case
                                messageType = conversationType,
                                deleteForEveryoneLabel = application.getString(R.string.deleteMessageEveryone),
                                warning = application.resources.getQuantityString(
                                    R.plurals.deleteMessageWarning, messages.count(), messages.count()
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * This delete the message locally only.
     * Attachments and other related data will be removed from the db.
     * If the messages were already marked as deleted they will be removed fully from the db,
     * otherwise they will appear as a special type of message
     * that says something like "This message was deleted"
     */
    fun deleteLocally(messages: Set<MessageRecord>) {
        // make sure to stop audio messages, if any
        messages.filterIsInstance<MmsMessageRecord>()
            .mapNotNull { it.slideDeck.audioSlide }
            .forEach(::stopMessageAudio)

        // if the message was already marked as deleted or control messages, remove it from the db instead
        if(messages.all { it.isDeleted || it.isControlMessage }){
            // Remove the message locally (leave nothing behind)
            repository.deleteMessages(messages = messages, threadId = threadId)
        } else {
            // only mark as deleted (message remains behind with "This message was deleted on this device" )
            repository.markAsDeletedLocally(
                messages = messages,
                displayedMessage = application.getString(R.string.deleteMessageDeletedLocally)
            )
        }

        // show confirmation toast
        Toast.makeText(
            application,
            application.resources.getQuantityString(R.plurals.deleteMessageDeleted, messages.count(), messages.count()),
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * This will mark the messages as deleted, for everyone.
     * Attachments and other related data will be removed from the db,
     * but the messages themselves won't be removed from the db.
     * Instead they will appear as a special type of message
     * that says something like "This message was deleted"
     */
    private fun markAsDeletedForEveryone(
        data: DeleteForEveryoneDialogData
    ) = viewModelScope.launch {
        val recipient = recipient ?: return@launch Log.w("Loki", "Recipient was null for delete for everyone - aborting delete operation.")

        // make sure to stop audio messages, if any
        data.messages.filterIsInstance<MmsMessageRecord>()
            .mapNotNull { it.slideDeck.audioSlide }
            .forEach(::stopMessageAudio)

        // the exact logic for this will depend on the messages type
        when(data.messageType){
            MessageType.NOTE_TO_SELF -> markAsDeletedForEveryoneNoteToSelf(data)
            MessageType.ONE_ON_ONE -> markAsDeletedForEveryone1On1(data)
            MessageType.LEGACY_GROUP -> markAsDeletedForEveryoneLegacyGroup(data.messages)
            MessageType.GROUPS_V2 -> markAsDeletedForEveryoneGroupsV2(data)
            MessageType.COMMUNITY -> markAsDeletedForEveryoneCommunity(data)
        }
    }

    private fun markAsDeletedForEveryoneNoteToSelf(data: DeleteForEveryoneDialogData){
        if(recipient == null) return showMessage(application.getString(R.string.errorUnknown))

        viewModelScope.launch(Dispatchers.IO) {
            // show a loading indicator
            _uiState.update { it.copy(showLoader = true) }

            // delete remotely
            try {
                repository.deleteNoteToSelfMessagesRemotely(threadId, recipient!!, data.messages)

                // When this is done we simply need to remove the message locally (leave nothing behind)
                repository.deleteMessages(messages = data.messages, threadId = threadId)

                // show confirmation toast
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        application,
                        application.resources.getQuantityString(
                            R.plurals.deleteMessageDeleted,
                            data.messages.count(),
                            data.messages.count()
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.w("Loki", "FAILED TO delete messages ${data.messages} ")
                // failed to delete - show a toast and get back on the modal
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        application,
                        application.resources.getQuantityString(
                            R.plurals.deleteMessageFailed,
                            data.messages.size,
                            data.messages.size
                        ), Toast.LENGTH_SHORT
                    ).show()
                }

                _dialogsState.update { it.copy(deleteEveryone = data) }
            }

            // hide loading indicator
            _uiState.update { it.copy(showLoader = false) }
        }
    }

    private fun markAsDeletedForEveryone1On1(data: DeleteForEveryoneDialogData){
        if(recipient == null) return showMessage(application.getString(R.string.errorUnknown))

        viewModelScope.launch(Dispatchers.IO) {
            // show a loading indicator
            _uiState.update { it.copy(showLoader = true) }

            // delete remotely
            try {
                repository.delete1on1MessagesRemotely(threadId, recipient!!, data.messages)

                // When this is done we simply need to remove the message locally
                repository.markAsDeletedLocally(
                    messages = data.messages,
                    displayedMessage = application.getString(R.string.deleteMessageDeletedGlobally)
                )

                // show confirmation toast
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        application,
                        application.resources.getQuantityString(
                            R.plurals.deleteMessageDeleted,
                            data.messages.count(),
                            data.messages.count()
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.w("Loki", "FAILED TO delete messages ${data.messages} ")
                // failed to delete - show a toast and get back on the modal
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        application,
                        application.resources.getQuantityString(
                            R.plurals.deleteMessageFailed,
                            data.messages.size,
                            data.messages.size
                        ), Toast.LENGTH_SHORT
                    ).show()
                }

                _dialogsState.update { it.copy(deleteEveryone = data) }
            }

            // hide loading indicator
            _uiState.update { it.copy(showLoader = false) }
        }
    }

    private fun markAsDeletedForEveryoneLegacyGroup(messages: Set<MessageRecord>){
        if(recipient == null) return showMessage(application.getString(R.string.errorUnknown))

        viewModelScope.launch(Dispatchers.IO) {
            // delete remotely
            try {
                repository.deleteLegacyGroupMessagesRemotely(recipient!!, messages)

                // When this is done we simply need to remove the message locally
                repository.markAsDeletedLocally(
                    messages = messages,
                    displayedMessage = application.getString(R.string.deleteMessageDeletedGlobally)
                )

                // show confirmation toast
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        application,
                        application.resources.getQuantityString(
                            R.plurals.deleteMessageDeleted,
                            messages.count(),
                            messages.count()
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.w("Loki", "FAILED TO delete messages ${messages} ")
                // failed to delete - show a toast and get back on the modal
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        application,
                        application.resources.getQuantityString(
                            R.plurals.deleteMessageFailed,
                            messages.size,
                            messages.size
                        ), Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun markAsDeletedForEveryoneGroupsV2(data: DeleteForEveryoneDialogData){
        viewModelScope.launch(Dispatchers.IO) {
            // show a loading indicator
            _uiState.update { it.copy(showLoader = true) }

            //todo GROUPS V2 - uncomment below and use Fanchao's method to delete a group V2
            try {
                //repository.callMethodFromFanchao(threadId, recipient, data.messages)

                // the repo will handle the internal logic (calling `/delete` on the swarm
                // and sending 'GroupUpdateDeleteMemberContentMessage'
                // When this is done we simply need to remove the message locally
                repository.markAsDeletedLocally(
                    messages = data.messages,
                    displayedMessage = application.getString(R.string.deleteMessageDeletedGlobally)
                )

                // show confirmation toast
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        application,
                        application.resources.getQuantityString(
                            R.plurals.deleteMessageDeleted,
                            data.messages.count(), data.messages.count()
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.w("Loki", "FAILED TO delete messages ${data.messages} ")
                // failed to delete - show a toast and get back on the modal
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        application,
                        application.resources.getQuantityString(
                            R.plurals.deleteMessageFailed,
                            data.messages.size,
                            data.messages.size
                        ), Toast.LENGTH_SHORT
                    ).show()
                }

                _dialogsState.update { it.copy(deleteEveryone = data) }
            }

            // hide loading indicator
            _uiState.update { it.copy(showLoader = false) }
        }
    }

    private fun markAsDeletedForEveryoneCommunity(data: DeleteForEveryoneDialogData){
        viewModelScope.launch(Dispatchers.IO) {
            // show a loading indicator
            _uiState.update { it.copy(showLoader = true) }

            // delete remotely
            try {
                repository.deleteCommunityMessagesRemotely(threadId, data.messages)

                // When this is done we simply need to remove the message locally (leave nothing behind)
                repository.deleteMessages(messages = data.messages, threadId = threadId)

                // show confirmation toast
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        application,
                        application.resources.getQuantityString(
                            R.plurals.deleteMessageDeleted,
                            data.messages.count(),
                            data.messages.count()
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.w("Loki", "FAILED TO delete messages ${data.messages} ")
                // failed to delete - show a toast and get back on the modal
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        application,
                        application.resources.getQuantityString(
                            R.plurals.deleteMessageFailed,
                            data.messages.size,
                            data.messages.size
                        ), Toast.LENGTH_SHORT
                    ).show()
                }

                _dialogsState.update { it.copy(deleteEveryone = data) }
            }

            // hide loading indicator
            _uiState.update { it.copy(showLoader = false) }
        }
    }

    private fun isUserCommunityManager() = openGroup?.let { openGroup ->
        val userPublicKey = textSecurePreferences.getLocalNumber() ?: return@let false
        OpenGroupManager.isUserModerator(application, openGroup.id, userPublicKey, blindedPublicKey)
    } ?: false

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

    fun banUser(recipient: Recipient) = viewModelScope.launch {
        repository.banUser(threadId, recipient)
            .onSuccess {
                showMessage(application.getString(R.string.banUserBanned))
            }
            .onFailure {
                showMessage(application.getString(R.string.banErrorFailed))
            }
    }

    fun banAndDeleteAll(messageRecord: MessageRecord) = viewModelScope.launch {

        repository.banAndDeleteAll(threadId, messageRecord.individualRecipient)
            .onSuccess {
                // At this point the server side messages have been successfully deleted..
                showMessage(application.getString(R.string.banUserBanned))

                // ..so we can now delete all their messages in this thread from local storage & remove the views.
                repository.deleteAllLocalMessagesInThreadFromSenderOfMessage(messageRecord)
            }
            .onFailure {
                showMessage(application.getString(R.string.banErrorFailed))
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

    fun onCommand(command: Commands) {
        when (command) {
            is Commands.ShowOpenUrlDialog -> {
                _dialogsState.update {
                    it.copy(openLinkDialogUrl = command.url)
                }
            }

            is Commands.HideDeleteEveryoneDialog -> {
                _dialogsState.update {
                    it.copy(deleteEveryone = null)
                }
            }

            is Commands.HideClearEmoji -> {
                _dialogsState.update {
                    it.copy(clearAllEmoji = null)
                }
            }

            is Commands.MarkAsDeletedLocally -> {
                // hide dialog first
                _dialogsState.update {
                    it.copy(deleteEveryone = null)
                }

                deleteLocally(command.messages)
            }
            is Commands.MarkAsDeletedForEveryone -> {
                markAsDeletedForEveryone(command.data)
            }


            is Commands.ClearEmoji -> {
                clearEmoji(command.emoji, command.messageId)
            }
        }
    }

    private fun clearEmoji(emoji: String, messageId: MessageId){
        viewModelScope.launch(Dispatchers.Default) {
            reactionDb.deleteEmojiReactions(emoji, messageId)
            openGroup?.let { openGroup ->
                lokiMessageDb.getServerID(messageId.id, !messageId.mms)?.let { serverId ->
                    OpenGroupApi.deleteAllReactions(
                        openGroup.room,
                        openGroup.server,
                        serverId,
                        emoji
                    )
                }
            }
            threadDb.notifyThreadUpdated(threadId)
        }
    }

    fun onEmojiClear(emoji: String, messageId: MessageId) {
        // show a confirmation dialog
        _dialogsState.update {
            it.copy(clearAllEmoji = ClearAllEmoji(emoji, messageId))
        }
    }

    fun onOptionItemSelected(
        // This must be the context of the activity as requirement from ConversationMenuHelper
        context: Context,
        item: MenuItem
    ): Boolean {
        val recipient = recipient ?: return false

        val inProgress = ConversationMenuHelper.onOptionItemSelected(
            context = context,
            item = item,
            thread = recipient,
            threadID = threadId,
            factory = configFactory,
            storage = storage,
            groupManager = groupManagerV2,
        )

        if (inProgress != null) {
            viewModelScope.launch {
                _uiState.update { it.copy(showLoader = true) }
                try {
                    inProgress.receive()
                } finally {
                    _uiState.update { it.copy(showLoader = false) }
                }
            }
        }

        return true
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
        private val storage: StorageProtocol,
        private val messageDataProvider: MessageDataProvider,
        private val groupDb: GroupDatabase,
        private val threadDb: ThreadDatabase,
        private val reactionDb: ReactionDatabase,
        @ApplicationContext
        private val context: Context,
        private val lokiMessageDb: LokiMessageDatabase,
        private val textSecurePreferences: TextSecurePreferences,
        private val configFactory: ConfigFactory,
        private val groupManagerV2: GroupManagerV2,
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConversationViewModel(
                threadId = threadId,
                edKeyPair = edKeyPair,
                application = application,
                repository = repository,
                storage = storage,
                messageDataProvider = messageDataProvider,
                groupDb = groupDb,
                threadDb = threadDb,
                reactionDb = reactionDb,
                lokiMessageDb = lokiMessageDb,
                textSecurePreferences = textSecurePreferences,
                configFactory = configFactory,
                groupManagerV2 = groupManagerV2,
            ) as T
        }
    }

    data class DialogsState(
        val openLinkDialogUrl: String? = null,
        val clearAllEmoji: ClearAllEmoji? = null,
        val deleteEveryone: DeleteForEveryoneDialogData? = null
    )

    data class DeleteForEveryoneDialogData(
        val messages: Set<MessageRecord>,
        val messageType: MessageType,
        val defaultToEveryone: Boolean,
        val everyoneEnabled: Boolean,
        val deleteForEveryoneLabel: String,
        val warning: String? = null
    )

    data class ClearAllEmoji(
        val emoji: String,
        val messageId: MessageId
    )

    sealed class Commands {
        data class ShowOpenUrlDialog(val url: String?) : Commands()

        data class ClearEmoji(val emoji:String, val messageId: MessageId) : Commands()

        data object HideDeleteEveryoneDialog : Commands()
        data object HideClearEmoji : Commands()

        data class MarkAsDeletedLocally(val messages: Set<MessageRecord>): Commands()
        data class MarkAsDeletedForEveryone(val data: DeleteForEveryoneDialogData): Commands()
    }
}

data class UiMessage(val id: Long, val message: String)

data class ConversationUiState(
    val uiMessages: List<UiMessage> = emptyList(),
    val messageRequestState: MessageRequestUiState = MessageRequestUiState.Invisible,
    val shouldExit: Boolean = false,
    val showInput: Boolean = true,
    val enableInputMediaControls: Boolean = true,
    val showLoader: Boolean = false
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
