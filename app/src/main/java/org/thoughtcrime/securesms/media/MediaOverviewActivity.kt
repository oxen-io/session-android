package org.thoughtcrime.securesms.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import org.session.libsession.utilities.Address
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.ui.setComposeContent
import javax.inject.Inject

@AndroidEntryPoint
class MediaOverviewActivity : PassphraseRequiredActionBarActivity() {
    @Inject
    lateinit var viewModelFactory: MediaOverviewViewModel.AssistedFactory

    private val viewModel: MediaOverviewViewModel by viewModels {
        viewModelFactory.create(IntentCompat.getParcelableExtra(intent, EXTRA_ADDRESS, Address::class.java)!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setComposeContent {
            MediaOverviewScreen(viewModel, onClose = this::finish)
        }

        supportActionBar?.hide()
    }

    companion object {
        private const val EXTRA_ADDRESS = "address"

        @JvmStatic
        fun createIntent(context: Context, address: Address): Intent {
            return Intent(context, MediaOverviewActivity::class.java).apply {
                putExtra(EXTRA_ADDRESS, address)
            }
        }
    }
}