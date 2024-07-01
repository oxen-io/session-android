package org.thoughtcrime.securesms.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.session.libsession.snode.SnodeModule
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.database.LokiAPIDatabaseProtocol
import org.session.libsignal.utilities.KeyHelper
import org.session.libsignal.utilities.hexEncodedPublicKey
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.KeyPairUtilities
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class State(val duration: Duration)

private val DONE_TIME = 1.seconds
private val DONE_ANIMATE_TIME = 500.milliseconds

private val TOTAL_ANIMATE_TIME = 14.seconds
private val TOTAL_TIME = 15.seconds

@HiltViewModel
class LoadingViewModel @Inject constructor(
    private val configFactory: ConfigFactory,
    private val prefs: TextSecurePreferences,
) : ViewModel() {

    private val state = MutableStateFlow(State(TOTAL_ANIMATE_TIME))
    val stateFlow = state.asStateFlow()

    private val event = Channel<Event>()
    val eventFlow = event.receiveAsFlow()

    private var restoreJob: Job? = null

    internal val database: LokiAPIDatabaseProtocol
        get() = SnodeModule.shared.storage

    fun restore(context: Context, seed: ByteArray) {

        // only have one sync job running at a time (prevent QR from trying to spawn a new job)
        if (restoreJob?.isActive == true) return

        restoreJob = viewModelScope.launch(Dispatchers.IO) {
            // This is here to resolve a case where the app restarts before a user completes onboarding
            // which can result in an invalid database state
            database.clearAllLastMessageHashes()
            database.clearReceivedMessageHashValues()

            // RestoreActivity handles seed this way
            val keyPairGenerationResult = KeyPairUtilities.generate(seed)
            val x25519KeyPair = keyPairGenerationResult.x25519KeyPair
            KeyPairUtilities.store(context, seed, keyPairGenerationResult.ed25519KeyPair, x25519KeyPair)
            configFactory.keyPairChanged()
            val userHexEncodedPublicKey = x25519KeyPair.hexEncodedPublicKey
            val registrationID = KeyHelper.generateRegistrationId(false)
            prefs.apply {
                setLocalRegistrationId(registrationID)
                setLocalNumber(userHexEncodedPublicKey)
                setRestorationTime(System.currentTimeMillis())
                setHasViewedSeed(true)
            }

            val skipJob = launch(Dispatchers.IO) {
                delay(TOTAL_TIME)
                event.send(Event.TIMEOUT)
            }

            // start polling and wait for updated message
            ApplicationContext.getInstance(context).apply { startPollingIfNeeded() }
            TextSecurePreferences.events.filter { it == TextSecurePreferences.CONFIGURATION_SYNCED }.collect {
                // handle we've synced
                skipJob.cancel()

                state.value = State(DONE_ANIMATE_TIME)
                delay(DONE_TIME)
                event.send(Event.SUCCESS)
            }
        }
    }
}

sealed interface Event {
    object SUCCESS: Event
    object TIMEOUT: Event
}
