package org.thoughtcrime.securesms.preferences.appearance

import android.os.Bundle
import android.os.PersistableBundle
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import javax.inject.Inject

@AndroidEntryPoint
class AppearanceSettingsActivity: PassphraseRequiredActionBarActivity() {

    @Inject
    lateinit var viewModel: AppearanceSettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
        setContentView(R.layout.activity_appearance_settings)
    }
}