package org.thoughtcrime.securesms.preferences

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isInvisible
import androidx.preference.Preference
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.ui.getSubbedCharSequence
import org.thoughtcrime.securesms.ui.getSubbedString

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
        private const val EXPORT_LOGS  = "export_logs"
        private const val TRANSLATE    = "translate_session"
        private const val FEEDBACK     = "feedback"
        private const val FAQ          = "faq"
        private const val SUPPORT      = "support"
        private const val CROWDIN_URL  = "https://getsession.org/translate"
        private const val FEEDBACK_URL = "https://getsession.org/survey"
        private const val FAQ_URL      = "https://getsession.org/faq"
        private const val SUPPORT_URL  = "https://sessionapp.zendesk.com/hc/en-us"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_help)

        // String sub the summary text of the `export_logs` element in preferences_help.xml
        val exportPref = preferenceScreen.findPreference<Preference>(EXPORT_LOGS)
        exportPref?.summary = context?.getSubbedCharSequence(R.string.helpReportABugExportLogsDescription, APP_NAME_KEY to getString(R.string.app_name))

        // String sub the summary text of the `translate_session` element in preferences_help.xml
        val translatePref = preferenceScreen.findPreference<Preference>(TRANSLATE)
        translatePref?.title = context?.getSubbedCharSequence(R.string.helpHelpUsTranslateSession, APP_NAME_KEY to getString(R.string.app_name))
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return when (preference.key) {
            EXPORT_LOGS -> {
                shareLogs()
                true
            }
            TRANSLATE -> {
                openLink(CROWDIN_URL)
                true
            }
            FEEDBACK -> {
                openLink(FEEDBACK_URL)
                true
            }
            FAQ -> {
                openLink(FAQ_URL)
                true
            }
            SUPPORT -> {
                openLink(SUPPORT_URL)
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }

    private fun updateExportButtonAndProgressBarUI(exportJobRunning: Boolean) {
        this.activity?.runOnUiThread(Runnable {
            // Change export logs button text
            val exportLogsButton = this.activity?.findViewById(R.id.export_logs_button) as TextView?
            if (exportLogsButton == null) { Log.w("Loki", "Could not find export logs button view.") }
            exportLogsButton?.text = if (exportJobRunning) getString(R.string.cancel) else getString(R.string.helpReportABugExportLogs)

            // Show progress bar
            val exportProgressBar = this.activity?.findViewById(R.id.export_progress_bar) as ProgressBar?
            exportProgressBar?.isInvisible = !exportJobRunning
        })
    }

    private fun shareLogs() {
        Permissions.with(this)
            .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .maxSdkVersion(Build.VERSION_CODES.P)
            .withPermanentDenialDialog(requireContext().getSubbedString(R.string.permissionsStorageSaveDenied, APP_NAME_KEY to getString(R.string.app_name)))
            .onAnyDenied {
                val c = requireContext()
                val txt = c.getSubbedString(R.string.permissionsStorageSaveDenied, APP_NAME_KEY to getString(R.string.app_name))
                Toast.makeText(c, txt, Toast.LENGTH_LONG).show()
            }
            .onAllGranted {
                ShareLogsDialog(::updateExportButtonAndProgressBarUI).show(parentFragmentManager,"Share Logs Dialog")
            }
            .execute()
    }

    private fun openLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireActivity(), requireContext().getString(R.string.errorUnknown), Toast.LENGTH_LONG).show()
        }
    }

}