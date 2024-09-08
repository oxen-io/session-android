package org.thoughtcrime.securesms.onboarding.messagenotifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.notifications.PushRegistry
import org.thoughtcrime.securesms.onboarding.manager.CreateAccountManager

internal class MessageNotificationsViewModel(
    private val state: State,
    private val application: Application,
    private val prefs: TextSecurePreferences,
    private val pushRegistry: PushRegistry,
    private val createAccountManager: CreateAccountManager
): AndroidViewModel(application) {
    private val _uiStates = MutableStateFlow(UiState())
    val uiStates = _uiStates.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    fun setEnabled(enabled: Boolean) {
        _uiStates.update { UiState(pushEnabled = enabled) }
    }

    fun onContinue() {
        viewModelScope.launch(Dispatchers.IO) {
            if (state is State.CreateAccount) createAccountManager.createAccount(state.displayName)

            prefs.setPushEnabled(uiStates.value.pushEnabled)
            pushRegistry.refresh(true)

            _events.emit(
                when (state) {
                    is State.CreateAccount -> Event.OnboardingComplete
                    else -> Event.Loading
                }
            )
        }
    }

    /**
     * @return [true] if the back press was handled.
     */
    fun onBackPressed(): Boolean = when (state) {
        is State.CreateAccount -> false
        is State.LoadAccount -> {
            _uiStates.update { it.copy(showingBackWarningDialogText = R.string.onboardingBackLoadAccount) }

            true
        }
    }

    fun dismissDialog() {
        _uiStates.update {
            it.copy(showingBackWarningDialogText = null)
        }
    }

    fun quit() {
        _uiStates.update { it.copy(clearData = true) }

        viewModelScope.launch(Dispatchers.IO) {
            ApplicationContext.getInstance(application).clearAllDataAndRestart()
        }
    }

    data class UiState(
        val pushEnabled: Boolean = true,
        val showingBackWarningDialogText: Int? = null,
        val clearData: Boolean = false
    ) {
        val pushDisabled get() = !pushEnabled
    }

    sealed interface State {
        class CreateAccount(val displayName: String): State
        object LoadAccount: State
    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(profileName: String?): Factory
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @AssistedInject constructor(
        @Assisted private val profileName: String?,
        private val application: Application,
        private val prefs: TextSecurePreferences,
        private val pushRegistry: PushRegistry,
        private val createAccountManager: CreateAccountManager,
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MessageNotificationsViewModel(
                state = profileName?.let(State::CreateAccount) ?: State.LoadAccount,
                application = application,
                prefs = prefs,
                pushRegistry = pushRegistry,
                createAccountManager = createAccountManager
            ) as T
        }
    }
}

enum class Event {
    OnboardingComplete, Loading
}
