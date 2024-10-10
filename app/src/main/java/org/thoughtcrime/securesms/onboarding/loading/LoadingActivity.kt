package org.thoughtcrime.securesms.onboarding.loading

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.home.startHomeActivity
import org.thoughtcrime.securesms.onboarding.pickname.startPickDisplayNameActivity
import org.thoughtcrime.securesms.ui.setComposeContent
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo
import javax.inject.Inject

@AndroidEntryPoint
class LoadingActivity: BaseActionBarActivity() {

    @Inject
    internal lateinit var configFactory: ConfigFactory

    @Inject
    internal lateinit var prefs: TextSecurePreferences

    private val viewModel: LoadingViewModel by viewModels()

    private fun register(loadFailed: Boolean) {
        when {
            loadFailed -> startPickDisplayNameActivity(loadFailed = true)
            else -> startHomeActivity(isNewAccount = false, isFromOnboarding = true)
        }

        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUpActionBarSessionLogo()

        setComposeContent {
            val progress by viewModel.progress.collectAsState()
            LoadingScreen(progress)
        }

        lifecycleScope.launch {
            viewModel.events.collect {
                when (it) {
                    Event.TIMEOUT -> register(loadFailed = true)
                    Event.SUCCESS -> register(loadFailed = false)
                }
            }
        }
    }
}
