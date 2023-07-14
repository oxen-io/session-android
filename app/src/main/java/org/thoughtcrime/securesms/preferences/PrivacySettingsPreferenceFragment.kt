package org.thoughtcrime.securesms.preferences

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.preference.Preference
import network.loki.messenger.BuildConfig
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.isPasswordDisabled
import org.session.libsession.utilities.TextSecurePreferences.Companion.setScreenLockEnabled
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.util.CallNotificationBuilder.Companion.areNotificationsEnabled
import org.thoughtcrime.securesms.util.IntentUtils

class PrivacySettingsPreferenceFragment : ListSummaryPreferenceFragment() {
    override fun onCreate(paramBundle: Bundle?) {
        super.onCreate(paramBundle)
        findPreference<Preference>(TextSecurePreferences.SCREEN_LOCK)!!
            .onPreferenceChangeListener = ScreenLockListener()
        findPreference<Preference>(TextSecurePreferences.TYPING_INDICATORS)!!
            .onPreferenceChangeListener = TypingIndicatorsToggleListener()
        findPreference<Preference>(TextSecurePreferences.CALL_NOTIFICATIONS_ENABLED)!!
            .onPreferenceChangeListener = CallToggleListener(this) { setCall(it) }
        initializeVisibility()
    }

    private fun setCall(isEnabled: Boolean) {
        (findPreference<Preference>(TextSecurePreferences.CALL_NOTIFICATIONS_ENABLED) as SwitchPreferenceCompat?)!!.isChecked =
            isEnabled
        if (isEnabled && !areNotificationsEnabled(requireActivity())) {
            // show a dialog saying that calls won't work properly if you don't have notifications on at a system level
            showSessionDialog {
                title(R.string.CallNotificationBuilder_system_notification_title)
                text(R.string.CallNotificationBuilder_system_notification_message)
                button(R.string.activity_notification_settings_title) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID))
                    }
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        .takeIf { IntentUtils.isResolvable(requireContext(), it) }.let {
                        startActivity(it)
                    }
                }
                button(R.string.dismiss)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_app_protection)
    }

    override fun onResume() {
        super.onResume()
    }

    private fun initializeVisibility() {
        if (isPasswordDisabled(requireContext())) {
            val keyguardManager =
                requireContext().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!keyguardManager.isKeyguardSecure) {
                findPreference<SwitchPreferenceCompat>(TextSecurePreferences.SCREEN_LOCK)!!.isChecked = false
                findPreference<Preference>(TextSecurePreferences.SCREEN_LOCK)!!.isEnabled = false
            }
        } else {
            findPreference<Preference>(TextSecurePreferences.SCREEN_LOCK)!!.isVisible = false
            findPreference<Preference>(TextSecurePreferences.SCREEN_LOCK_TIMEOUT)!!.isVisible = false
        }
    }

    private inner class ScreenLockListener : Preference.OnPreferenceChangeListener {
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            val enabled = newValue as Boolean
            setScreenLockEnabled(context!!, enabled)
            val intent = Intent(context, KeyCachingService::class.java)
            intent.action = KeyCachingService.LOCK_TOGGLED_EVENT
            context!!.startService(intent)
            return true
        }
    }

    private inner class TypingIndicatorsToggleListener : Preference.OnPreferenceChangeListener {
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            val enabled = newValue as Boolean
            if (!enabled) {
                ApplicationContext.getInstance(requireContext()).typingStatusRepository.clear()
            }
            return true
        }
    }

}