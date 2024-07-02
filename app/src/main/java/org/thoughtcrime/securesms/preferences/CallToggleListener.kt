package org.thoughtcrime.securesms.preferences

import android.Manifest
import android.app.Activity
import android.content.Context
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.setBooleanPreference
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.permissions.requestMicrophonePermission
import org.thoughtcrime.securesms.showSessionDialog

internal class CallToggleListener(
    private val fragment: Fragment,
    private val callback: (Boolean) -> Unit
) : Preference.OnPreferenceChangeListener {

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        if (newValue == false) return true

        // check if we've shown the info dialog and check for microphone permissions
        fragment.showSessionDialog {
            title(R.string.dialog_voice_video_title)
            text(R.string.dialog_voice_video_message)
            button(R.string.dialog_link_preview_enable_button_title, R.string.AccessibilityId_enable) { fragment.requestMicrophonePermission(callback) }
            cancelButton()
        }

        return false
    }
}
