package org.thoughtcrime.securesms.preferences

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.permissions.Permissions

class HelpSettingsActivity: PassphraseRequiredActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        setContentView(R.layout.activity_fragment_wrapper)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, HelpSettingsFragment())
            .commit()
    }
}

class HelpSettingsFragment: CorrectedPreferenceFragment() {

    companion object {
        private const val EXPORT_LOGS = "export_logs"
        private const val TRANSLATE = "translate_session"
        private const val FEEDBACK = "feedback"
        private const val FAQ = "faq"
        private const val SUPPORT = "support"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_help)
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        preference ?: return false
        return when (preference.key) {
            EXPORT_LOGS -> {
                shareLogs()
                true
            }
            TRANSLATE -> {
                helpTranslate()
                true
            }
            FEEDBACK -> {

                true
            }
            FAQ -> {

                true
            }
            SUPPORT -> {

                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }

    private fun shareLogs() {
        Permissions.with(this)
            .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .maxSdkVersion(Build.VERSION_CODES.P)
            .withPermanentDenialDialog(getString(R.string.MediaPreviewActivity_signal_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied))
            .onAnyDenied {
                Toast.makeText(requireActivity(), R.string.MediaPreviewActivity_unable_to_write_to_external_storage_without_permission, Toast.LENGTH_LONG).show()
            }
            .onAllGranted {
                ShareLogsDialog().show(parentFragmentManager,"Share Logs Dialog")
            }
            .execute()
    }

    private fun helpTranslate() {
        try {
            val url = "https://crowdin.com/project/session-android"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireActivity(), "Can't open URL", Toast.LENGTH_LONG).show()
        }
    }

}