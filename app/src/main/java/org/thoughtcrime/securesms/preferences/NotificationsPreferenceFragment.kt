package org.thoughtcrime.securesms.preferences

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.preference.ListPreference
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.preferences.widgets.DropDownPreference
import java.util.Arrays
import javax.inject.Inject

@AndroidEntryPoint
class NotificationsPreferenceFragment : CorrectedPreferenceFragment() {
    @Inject
    lateinit var prefs: TextSecurePreferences

    override fun onCreate(paramBundle: Bundle?) {
        super.onCreate(paramBundle)

        // Set up FCM toggle
        val fcmKey = "pref_key_use_fcm"
        val fcmPreference: SwitchPreferenceCompat = findPreference(fcmKey)!!
        fcmPreference.isChecked = prefs.pushEnabled.value
        fcmPreference.setOnPreferenceChangeListener { _: Preference, newValue: Any ->
                prefs.setPushEnabled(newValue as Boolean)
                true
            }

        prefs.setNotificationRingtone(
            NotificationChannels.getMessageRingtone(requireContext()).toString()
        )
        prefs.setNotificationVibrateEnabled(
            NotificationChannels.getMessageVibrate(requireContext())
        )

        findPreference<DropDownPreference>(TextSecurePreferences.RINGTONE_PREF)?.apply {
            setOnViewReady { updateRingtonePref() }
            onPreferenceChangeListener = RingtoneSummaryListener()
        }

        findPreference<DropDownPreference>(TextSecurePreferences.NOTIFICATION_PRIVACY_PREF)?.apply {
            setOnViewReady { setDropDownLabel(entry) }
            onPreferenceChangeListener = NotificationPrivacyListener()
        }

        findPreference<Preference>(TextSecurePreferences.VIBRATE_PREF)!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                NotificationChannels.updateMessageVibrate(requireContext(), newValue as Boolean)
                true
            }

        findPreference<Preference>(TextSecurePreferences.RINGTONE_PREF)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val current = prefs.getNotificationRingtone()
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                intent.putExtra(
                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                    RingtoneManager.TYPE_NOTIFICATION
                )
                intent.putExtra(
                    RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                    Settings.System.DEFAULT_NOTIFICATION_URI
                )
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
                startActivityForResult(intent, 1)
                true
            }

        findPreference<Preference>(TextSecurePreferences.NOTIFICATION_PRIORITY_PREF)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                intent.putExtra(
                    Settings.EXTRA_CHANNEL_ID,
                    NotificationChannels.getMessagesChannel(requireContext())
                )
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                startActivity(intent)
                true
            }

        initializeMessageVibrateSummary(findPreference<Preference>(TextSecurePreferences.VIBRATE_PREF) as SwitchPreferenceCompat?)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_notifications)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1 && resultCode == Activity.RESULT_OK && data != null) {
            var uri = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (Settings.System.DEFAULT_NOTIFICATION_URI == uri) {
                NotificationChannels.updateMessageRingtone(requireContext(), uri)
                prefs.removeNotificationRingtone()
            } else {
                uri = uri ?: Uri.EMPTY
                NotificationChannels.updateMessageRingtone(requireContext(), uri)
                prefs.setNotificationRingtone(uri.toString())
            }
            updateRingtonePref()
        }
    }

    private inner class RingtoneSummaryListener : Preference.OnPreferenceChangeListener {
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            val pref = preference as? DropDownPreference ?: return false
            val value = newValue as? Uri
            if (value == null || TextUtils.isEmpty(value.toString())) {
                pref.setDropDownLabel(context?.getString(R.string.none))
            } else {
                RingtoneManager.getRingtone(activity, value)
                    ?.getTitle(activity)
                    ?.let { pref.setDropDownLabel(it) }

            }
            return true
        }
    }

    private fun updateRingtonePref() {
        val pref = findPreference<Preference>(TextSecurePreferences.RINGTONE_PREF)
        val listener: RingtoneSummaryListener =
            (pref?.onPreferenceChangeListener) as? RingtoneSummaryListener
                ?: return

        val uri = prefs.getNotificationRingtone()
        listener.onPreferenceChange(pref, uri)
    }

    private fun initializeMessageVibrateSummary(pref: SwitchPreferenceCompat?) {
        pref!!.isChecked = prefs.isNotificationVibrateEnabled()
    }

    private inner class NotificationPrivacyListener : Preference.OnPreferenceChangeListener {
        @SuppressLint("StaticFieldLeak")
        override fun onPreferenceChange(preference: Preference, value: Any): Boolean {
            // update drop down
            val pref = preference as? DropDownPreference ?: return false
            val entryIndex = Arrays.asList(*pref.entryValues).indexOf(value)

            pref.setDropDownLabel(
                if (entryIndex >= 0 && entryIndex < pref.entries.size
                ) pref.entries[entryIndex]
                else getString(R.string.unknown)
            )

            // update notification
            object : AsyncTask<Void?, Void?, Void?>() {
                override fun doInBackground(vararg params: Void?): Void? {
                    ApplicationContext.getInstance(activity).messageNotifier.updateNotification(
                        activity!!
                    )
                    return null
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
            return true
        }
    }
}
