package org.thoughtcrime.securesms.preferences.appearance

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.children
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityAppearanceSettingsBinding
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity

@AndroidEntryPoint
class AppearanceSettingsActivity: PassphraseRequiredActionBarActivity(), View.OnClickListener {

    val viewModel: AppearanceSettingsViewModel by viewModels()
    lateinit var binding : ActivityAppearanceSettingsBinding

    private val accentColors
        get() = mapOf(
            binding.accentGreen to R.style.PrimaryGreen,
            binding.accentBlue to R.style.PrimaryBlue,
            binding.accentYellow to R.style.PrimaryYellow,
            binding.accentPink to R.style.PrimaryPink,
            binding.accentPurple to R.style.PrimaryPurple,
            binding.accentOrange to R.style.PrimaryOrange,
            binding.accentRed to R.style.PrimaryRed
        )

    override fun onClick(v: View?) {
        v ?: return
        val accents = accentColors
        val entry = accents[v]
        entry?.let { viewModel.setNewAccent(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        binding = ActivityAppearanceSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar!!.title = getString(R.string.activity_settings_message_appearance_button_title)
        with (binding) {
            accentContainer.children.forEach { view ->
                view.setOnClickListener(this@AppearanceSettingsActivity)
            }
        }

        viewModel.themeUpdates.

    }
}