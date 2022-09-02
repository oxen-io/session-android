package org.thoughtcrime.securesms.preferences.appearance

import androidx.annotation.StyleRes
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.conversation.v2.ConversationUiState
import javax.inject.Inject

@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor(private val prefs: TextSecurePreferences) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState

    private fun buildThemeState() = prefs

    fun setNewAccent(@StyleRes accentColorStyle: Int) {
        prefs.setAccentColorStyle(accentColorStyle)
    }

    data class ThemeState (
        @StyleRes val theme: Int,
        @StyleRes val accent: Int
    )

}