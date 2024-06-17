package org.thoughtcrime.securesms.onboarding.loading

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.ApplicationContext
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
    lateinit var configFactory: ConfigFactory

    @Inject
    lateinit var prefs: TextSecurePreferences

    private val viewModel: LoadingViewModel by viewModels()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        return
    }

    private fun register(loadFailed: Boolean) {
        prefs.setLastConfigurationSyncTime(System.currentTimeMillis())

        when {
            loadFailed -> startPickDisplayNameActivity(
                loadFailed = true,
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
            else -> startHomeActivity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ApplicationContext.getInstance(this).newAccount = false

        setComposeContent {
            val state by viewModel.stateFlow.collectAsState()
            LoadingScreen(state)
        }

        setUpActionBarSessionLogo(true)

        lifecycleScope.launch {
            viewModel.eventFlow.collect {
                when (it) {
                    Event.TIMEOUT -> register(skipped = true)
                    Event.SUCCESS -> register(skipped = false)
                }
            }
        }
    }
}