package org.thoughtcrime.securesms.onboarding.pickname

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.home.startHomeActivity
import org.thoughtcrime.securesms.onboarding.messagenotifications.startMessageNotificationsActivity
import org.thoughtcrime.securesms.ui.setComposeContent
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo
import javax.inject.Inject

private const val EXTRA_LOAD_FAILED = "extra_load_failed"

@AndroidEntryPoint
class PickDisplayNameActivity : BaseActionBarActivity() {

    @Inject
    internal lateinit var viewModelFactory: PickDisplayNameViewModel.AssistedFactory
    @Inject
    internal lateinit var prefs: TextSecurePreferences

    private val loadFailed get() = intent.getBooleanExtra(EXTRA_LOAD_FAILED, false)

    private val viewModel: PickDisplayNameViewModel by viewModels {
        viewModelFactory.create(loadFailed)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo()

        setComposeContent { DisplayNameScreen(viewModel) }

        lifecycleScope.launch(Dispatchers.Main) {
            viewModel.events.collect {
                when (it) {
                    is Event.CreateAccount -> startMessageNotificationsActivity(it.profileName)
                    Event.LoadAccountComplete -> startHomeActivity(isNewAccount = false, isFromOnboarding = true)
                }
            }
        }
    }

    @Composable
    private fun DisplayNameScreen(viewModel: PickDisplayNameViewModel) {
        PickDisplayName(
            viewModel.states.collectAsState().value,
            viewModel::onChange,
            viewModel::onContinue,
            viewModel::dismissDialog,
            quit = { viewModel.dismissDialog(); finish() }
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (viewModel.onBackPressed()) return

        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}

fun Context.startPickDisplayNameActivity(loadFailed: Boolean = false, flags: Int = 0) {
    Intent(this, PickDisplayNameActivity::class.java)
        .apply { putExtra(EXTRA_LOAD_FAILED, loadFailed) }
        .also { it.flags = flags }
        .also(::startActivity)
}
