package org.thoughtcrime.securesms.onboarding.messagenotifications

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class MessageNotificationsViewModel @Inject constructor(): ViewModel() {
    private val state = MutableStateFlow(MessageNotificationsState())
    val stateFlow = state.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        state.update { MessageNotificationsState(pushEnabled = enabled) }
    }
}

data class MessageNotificationsState(val pushEnabled: Boolean = true) {
    val pushDisabled get() = !pushEnabled
}
