package org.thoughtcrime.securesms.onboarding.pickname

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

class PickDisplayNameViewModel(
    pickNewName: Boolean,
    private val prefs: TextSecurePreferences,
    private val configFactory: ConfigFactory
): ViewModel() {
    private val state = MutableStateFlow(if (pickNewName) pickNewNameState() else State())
    val stateFlow = state.asStateFlow()

    private val event = Channel<Event>()
    val eventFlow = event.receiveAsFlow()

    private val database: LokiAPIDatabaseProtocol
        get() = SnodeModule.shared.storage

    fun onContinue(context: Context) {
        state.update { it.copy(displayName = it.displayName.trim()) }

        val displayName = state.value.displayName

        val keyPairGenerationResult = KeyPairUtilities.generate()
        val seed = keyPairGenerationResult.seed
        val ed25519KeyPair = keyPairGenerationResult.ed25519KeyPair
        val x25519KeyPair = keyPairGenerationResult.x25519KeyPair

        when {
            displayName.isEmpty() -> { state.update { it.copy(error = R.string.displayNameErrorDescription) } }
            displayName.length > NAME_PADDED_LENGTH -> { state.update { it.copy(error = R.string.displayNameErrorDescriptionShorter) } }
            else -> {
                prefs.setProfileName(displayName)

                // This is here to resolve a case where the app restarts before a user completes onboarding
                // which can result in an invalid database state
                database.clearAllLastMessageHashes()
                database.clearReceivedMessageHashValues()

                KeyPairUtilities.store(context, seed, ed25519KeyPair, x25519KeyPair)
                configFactory.keyPairChanged()
                val userHexEncodedPublicKey = x25519KeyPair.hexEncodedPublicKey
                val registrationID = KeyHelper.generateRegistrationId(false)
                prefs.setLocalRegistrationId(registrationID)
                prefs.setLocalNumber(userHexEncodedPublicKey)
                prefs.setRestorationTime(0)
                prefs.setHasViewedSeed(false)

                viewModelScope.launch { event.send(Event.DONE) }
            }
        }
    }

    fun onChange(value: String) {
        state.update { state -> state.copy(
                displayName = value,
                error = value.takeIf { it.length > NAME_PADDED_LENGTH }?.let { R.string.displayNameErrorDescriptionShorter }
            )
        }
    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(pickNewName: Boolean): Factory
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @AssistedInject constructor(
        @Assisted private val pickNewName: Boolean,
        private val prefs: TextSecurePreferences,
        private val configFactory: ConfigFactory
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PickDisplayNameViewModel(pickNewName, prefs, configFactory) as T
        }
    }
}

data class State(
    @StringRes val title: Int = R.string.displayNamePick,
    @StringRes val description: Int = R.string.displayNameDescription,
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
