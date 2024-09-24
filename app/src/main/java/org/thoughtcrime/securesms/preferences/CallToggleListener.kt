package org.thoughtcrime.securesms.preferences

import android.Manifest
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.setBooleanPreference
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.ui.getSubbedString

internal class CallToggleListener(
    private val context: Fragment,
    private val setCallback: (Boolean) -> Unit
) : Preference.OnPreferenceChangeListener {

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        if (newValue == false) return true

        // check if we've shown the info dialog and check for microphone permissions
        context.showSessionDialog {
            title(R.string.callsVoiceAndVideoBeta)
            text(R.string.callsVoiceAndVideoModalDescription)
            button(R.string.enable, R.string.AccessibilityId_enable) { requestMicrophonePermission() }
            cancelButton()
        }

        return false
    }

    private fun requestMicrophonePermission() {
        Permissions.with(context)
            .request(Manifest.permission.RECORD_AUDIO)
            .onAllGranted {
                setBooleanPreference(
                    context.requireContext(),
                    TextSecurePreferences.CALL_NOTIFICATIONS_ENABLED,
                    true
                )
                setCallback(true)
            }
            .withPermanentDenialDialog(
                context.requireContext().getSubbedString(R.string.permissionsMicrophoneAccessRequired,
                APP_NAME_KEY to context.requireContext().getString(R.string.app_name)
                ))
            .onAnyDenied { setCallback(false) }
            .execute()
    }
}
