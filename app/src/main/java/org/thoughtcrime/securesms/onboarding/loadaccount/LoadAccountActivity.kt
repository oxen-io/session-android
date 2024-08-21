package org.thoughtcrime.securesms.onboarding.loadaccount

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.onboarding.manager.LoadAccountManager
import org.thoughtcrime.securesms.onboarding.messagenotifications.MessageNotificationsActivity
import org.thoughtcrime.securesms.ui.setComposeContent
import org.thoughtcrime.securesms.util.start

@AndroidEntryPoint
class LoadAccountActivity : BaseActionBarActivity() {

    @Inject
    internal lateinit var prefs: TextSecurePreferences
    @Inject
    internal lateinit var loadAccountManager: LoadAccountManager

    private val viewModel: LoadAccountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setTitle(R.string.loadAccount)
        prefs.setConfigurationMessageSynced(false)
        prefs.setRestorationTime(System.currentTimeMillis())
        prefs.setLastProfileUpdateTime(0)

        lifecycleScope.launch {
            viewModel.events.collect {
                loadAccountManager.load(it.mnemonic)
                start<MessageNotificationsActivity>()
            }
        }

        setComposeContent {
            val state by viewModel.stateFlow.collectAsState()
            LoadAccountScreen(state, viewModel.qrErrors, viewModel::onChange, viewModel::onContinue, viewModel::onScanQrCode)
        }
    }
}
