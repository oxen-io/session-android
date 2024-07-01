package org.thoughtcrime.securesms.onboarding.pickname

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.snode.SnodeModule
import org.session.libsession.utilities.SSKEnvironment.ProfileManagerProtocol.Companion.NAME_PADDED_LENGTH
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.database.LokiAPIDatabaseProtocol
import org.session.libsignal.utilities.KeyHelper
import org.session.libsignal.utilities.hexEncodedPublicKey
import org.thoughtcrime.securesms.crypto.KeyPairUtilities
import org.thoughtcrime.securesms.dependencies.ConfigFactory

internal class PickDisplayNameViewModel(
    private val loadFailed: Boolean,
    private val prefs: TextSecurePreferences,
    private val configFactory: ConfigFactory
): ViewModel() {
    private val _states = MutableStateFlow(if (loadFailed) pickNewNameState() else State())
    val states = _states.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    private val database: LokiAPIDatabaseProtocol
        get() = SnodeModule.shared.storage

    fun onContinue(context: Context) {
        _states.update { it.copy(displayName = it.displayName.trim()) }

        val displayName = _states.value.displayName

        when {
            displayName.isEmpty() -> { _states.update { it.copy(isTextErrorColor = true, error = R.string.displayNameErrorDescription) } }
            displayName.toByteArray().size > NAME_PADDED_LENGTH -> { _states.update { it.copy(isTextErrorColor = true, error = R.string.displayNameErrorDescriptionShorter) } }
            else -> {
                _states.update { it.copy(isTextErrorColor = false, error = null) }

                prefs.setProfileName(displayName)

                if (!loadFailed) {
                    // This is here to resolve a case where the app restarts before a user completes onboarding
                    // which can result in an invalid database state
                    database.clearAllLastMessageHashes()
                    database.clearReceivedMessageHashValues()

                    val keyPairGenerationResult = KeyPairUtilities.generate()
                    val seed = keyPairGenerationResult.seed
                    val ed25519KeyPair = keyPairGenerationResult.ed25519KeyPair
                    val x25519KeyPair = keyPairGenerationResult.x25519KeyPair

                    KeyPairUtilities.store(context, seed, ed25519KeyPair, x25519KeyPair)
                    configFactory.keyPairChanged()
                    val userHexEncodedPublicKey = x25519KeyPair.hexEncodedPublicKey
                    val registrationID = KeyHelper.generateRegistrationId(false)
                    prefs.setLocalRegistrationId(registrationID)
                    prefs.setLocalNumber(userHexEncodedPublicKey)
                    prefs.setRestorationTime(0)
                }

                viewModelScope.launch { _events.emit(Event.DONE) }
            }
        }
    }

    fun onChange(value: String) {
        _states.update { state ->
            state.copy(
                displayName = value,
                isTextErrorColor = false
            )
        }
    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(loadFailed: Boolean): Factory
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @AssistedInject constructor(
        @Assisted private val loadFailed: Boolean,
        private val prefs: TextSecurePreferences,
        private val configFactory: ConfigFactory
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PickDisplayNameViewModel(loadFailed, prefs, configFactory) as T
        }
    }
}

data class State(
    @StringRes val title: Int = R.string.displayNamePick,
    @StringRes val description: Int = R.string.displayNameDescription,
    val isTextErrorColor: Boolean = false,
    @StringRes val error: Int? = null,
    val displayName: String = ""
)

fun pickNewNameState() = State(
    title = R.string.displayNameNew,
    description = R.string.displayNameErrorNew
)

sealed interface Event {
    object DONE: Event
}
