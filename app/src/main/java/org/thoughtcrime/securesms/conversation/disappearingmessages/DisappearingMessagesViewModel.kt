package org.thoughtcrime.securesms.conversation.disappearingmessages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.loki.messenger.BuildConfig
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.utilities.SSKEnvironment.MessageExpirationManagerProtocol
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.conversation.disappearingmessages.ui.ExpiryCallbacks
import org.thoughtcrime.securesms.conversation.disappearingmessages.ui.UiState
import org.thoughtcrime.securesms.conversation.disappearingmessages.ui.toUiState
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase

class DisappearingMessagesViewModel(
    private val threadId: Long,
    private val application: Application,
    private val textSecurePreferences: TextSecurePreferences,
    private val messageExpirationManager: MessageExpirationManagerProtocol,
    private val disappearingMessages: DisappearingMessages,
    private val threadDb: ThreadDatabase,
    private val groupDb: GroupDatabase,
    private val storage: Storage,
    isNewConfigEnabled: Boolean,
    showDebugOptions: Boolean
) : AndroidViewModel(application), ExpiryCallbacks {

    private val _event = Channel<Event>()
    val event = _event.receiveAsFlow()

    private val _state = MutableStateFlow(
        State(
            isNewConfigEnabled = isNewConfigEnabled,
            showDebugOptions = showDebugOptions
        )
    )
    val state = _state.asStateFlow()

    val uiState = _state
        .map(State::toUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init {
        viewModelScope.launch {
            val expiryMode = storage.getExpirationConfiguration(threadId)?.expiryMode?.maybeConvertToLegacy(isNewConfigEnabled) ?: ExpiryMode.NONE
            val recipient = threadDb.getRecipientForThreadId(threadId)
            val groupRecord = recipient?.takeIf { it.isClosedGroupRecipient }
                ?.run { groupDb.getGroup(address.toGroupString()).orNull() }

            _state.update {
                it.copy(
                    address = recipient?.address,
                    isGroup = groupRecord != null,
                    isNoteToSelf = recipient?.address?.serialize() == textSecurePreferences.getLocalNumber(),
                    isSelfAdmin = groupRecord == null || groupRecord.admins.any{ it.serialize() == textSecurePreferences.getLocalNumber() },
                    expiryMode = expiryMode,
                    persistedMode = expiryMode
                )
            }
        }
    }

    override fun setValue(value: ExpiryMode) = _state.update { it.copy(expiryMode = value) }

    override fun onSetClick() = viewModelScope.launch {
        val state = _state.value
        val mode = state.expiryMode?.coerceLegacyToAfterSend()
        val address = state.address
        if (address == null || mode == null) {
            _event.send(Event.FAIL)
            return@launch
        }

        disappearingMessages.set(threadId, address, mode, state.isGroup)

        _event.send(Event.SUCCESS)
    }

    private fun ExpiryMode.coerceLegacyToAfterSend() = takeUnless { it is ExpiryMode.Legacy } ?: ExpiryMode.AfterSend(expirySeconds)

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(threadId: Long): Factory
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @AssistedInject constructor(
        @Assisted private val threadId: Long,
        private val application: Application,
        private val textSecurePreferences: TextSecurePreferences,
        private val messageExpirationManager: MessageExpirationManagerProtocol,
        private val disappearingMessages: DisappearingMessages,
        private val threadDb: ThreadDatabase,
        private val groupDb: GroupDatabase,
        private val storage: Storage
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T = DisappearingMessagesViewModel(
            threadId,
            application,
            textSecurePreferences,
            messageExpirationManager,
            disappearingMessages,
            threadDb,
            groupDb,
            storage,
            ExpirationConfiguration.isNewConfigEnabled,
            BuildConfig.DEBUG
        ) as T
    }
}

private fun ExpiryMode.maybeConvertToLegacy(isNewConfigEnabled: Boolean): ExpiryMode = takeIf { isNewConfigEnabled } ?: ExpiryMode.Legacy(expirySeconds)
