package org.thoughtcrime.securesms.preferences

import android.Manifest
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import network.loki.messenger.R
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.utilities.prefs
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.showSessionDialog

internal class CallToggleListener(
    private val fragment: Fragment,
    private val setCallback: (Boolean) -> Unit
) : Preference.OnPreferenceChangeListener {

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        if (newValue == false) return true

        // check if we've shown the info dialog and check for microphone permissions
        fragment.showSessionDialog {
            title(R.string.dialog_voice_video_title)
            text(R.string.dialog_voice_video_message)
            button(R.string.dialog_link_preview_enable_button_title, R.string.AccessibilityId_enable) { requestMicrophonePermission() }
            cancelButton()
        }

        return false
    }

    private fun requestMicrophonePermission() {
        Permissions.with(fragment)
            .request(Manifest.permission.RECORD_AUDIO)
            .onAllGranted {
                fragment.requireContext().prefs.setCallNotificationsEnabled(true)
                setCallback(true)
            }
            .onAnyDenied { setCallback(false) }
            .execute()
    }
}
