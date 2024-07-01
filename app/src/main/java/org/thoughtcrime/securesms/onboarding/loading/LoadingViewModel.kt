package org.thoughtcrime.securesms.onboarding.loading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.session.libsession.utilities.TextSecurePreferences
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class State(val duration: Duration)

private val ANIMATE_TO_DONE_TIME = 500.milliseconds
private val IDLE_DONE_TIME = 1.seconds
private val TIMEOUT_TIME = 15.seconds

@OptIn(FlowPreview::class)
@HiltViewModel
internal class LoadingViewModel @Inject constructor(
    val prefs: TextSecurePreferences
): ViewModel() {

    private val _states = MutableStateFlow(State(TIMEOUT_TIME))
    val states = _states.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                TextSecurePreferences.events
                    .filter { it == TextSecurePreferences.CONFIGURATION_SYNCED }
                    .onStart { emit(TextSecurePreferences.CONFIGURATION_SYNCED) }
                    .filter { prefs.getConfigurationMessageSynced() }
                    .timeout(TIMEOUT_TIME)
                    .collectLatest { onSuccess() }
            } catch (e: Exception) {
                onFail()
            }
        }
    }

    private suspend fun onSuccess() {
        withContext(Dispatchers.Main) {
            _states.value = State(ANIMATE_TO_DONE_TIME)
            delay(IDLE_DONE_TIME)
            _events.emit(Event.SUCCESS)
        }
    }

    private suspend fun onFail() {
        withContext(Dispatchers.Main) {
            _events.emit(Event.TIMEOUT)
        }
    }
}

sealed interface Event {
    object SUCCESS: Event
    object TIMEOUT: Event
}
