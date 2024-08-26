package org.thoughtcrime.securesms.debugmenu

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.session.libsession.utilities.Environment
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import javax.inject.Inject

@HiltViewModel
class DebugMenuViewModel @Inject constructor(
    private val application: Application,
    private val textSecurePreferences: TextSecurePreferences
) : ViewModel() {
    private val TAG = "DebugMenu"

    private val _uiState = MutableStateFlow(
        UIState(
            currentEnvironment = textSecurePreferences.getEnvironment().label,
            environments = Environment.entries.map { it.label },
            snackMessage = null,
            showEnvironmentWarningDialog = false,
            showEnvironmentLoadingDialog = false
        )
    )
    val uiState: StateFlow<UIState>
        get() = _uiState

    private var temporaryEnv: Environment? = null

    fun onCommand(command: Commands) {
        when (command) {
            is Commands.ChangeEnvironment -> changeEnvironment()

            is Commands.HideEnvironmentWarningDialog -> _uiState.value =
                _uiState.value.copy(showEnvironmentWarningDialog = false)

            is Commands.ShowEnvironmentWarningDialog ->
                showEnvironmentWarningDialog(command.environment)
        }
    }

    private fun showEnvironmentWarningDialog(environment: String) {
        if(environment == _uiState.value.currentEnvironment) return
        val env = Environment.entries.firstOrNull { it.label == environment } ?: return

        temporaryEnv = env

        _uiState.value = _uiState.value.copy(showEnvironmentWarningDialog = true)
    }

    private fun changeEnvironment() {
        val env = temporaryEnv ?: return

        // show a loading state
        _uiState.value = _uiState.value.copy(
            showEnvironmentWarningDialog = false,
            showEnvironmentLoadingDialog = true
        )

        // clear remote and local data, then restart the app
        viewModelScope.launch {
            try {
                ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(application).get()
            } catch (e: Exception) {
                // we can ignore fails here as we might be switching environments before the user gets a public key
            }
            ApplicationContext.getInstance(application).clearAllData().let { success ->
                if(success){
                    // save the environment
                    textSecurePreferences.setEnvironment(env)
                    delay(500)
                    ApplicationContext.getInstance(application).restartApplication()
                } else {
                    _uiState.value = _uiState.value.copy(
                        showEnvironmentWarningDialog = false,
                        showEnvironmentLoadingDialog = false
                    )
                    Log.e(TAG, "Failed to force sync when deleting data")
                    _uiState.value = _uiState.value.copy(snackMessage = "Sorry, something went wrong...")
                    return@launch
                }
            }
        }
    }

    data class UIState(
        val currentEnvironment: String,
        val environments: List<String>,
        val snackMessage: String?,
        val showEnvironmentWarningDialog: Boolean,
        val showEnvironmentLoadingDialog: Boolean
    )

    sealed class Commands {
        object ChangeEnvironment : Commands()
        data class ShowEnvironmentWarningDialog(val environment: String) : Commands()
        object HideEnvironmentWarningDialog : Commands()
    }
}