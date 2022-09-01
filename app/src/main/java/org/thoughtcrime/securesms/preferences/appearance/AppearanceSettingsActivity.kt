package org.thoughtcrime.securesms.preferences.appearance

import android.os.Bundle
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity

@AndroidEntryPoint
class AppearanceSettingsActivity: PassphraseRequiredActionBarActivity() {

    val viewModel: AppearanceSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        setContentView(R.layout.activity_appearance_settings)
        supportActionBar!!.title = getString(R.string.activity_settings_message_appearance_button_title)
    }
}