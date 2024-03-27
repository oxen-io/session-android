package org.thoughtcrime.securesms.preferences

import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityBlockedContactsBinding
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.showSessionDialog

@AndroidEntryPoint
class BlockedContactsActivity: PassphraseRequiredActionBarActivity() {

    lateinit var binding: ActivityBlockedContactsBinding

    val viewModel: BlockedContactsViewModel by viewModels()

    val adapter: BlockedContactsAdapter by lazy { BlockedContactsAdapter(viewModel) }

    fun unblock() {
        showSessionDialog {
            title(viewModel.getTitle(this@BlockedContactsActivity))
            text(viewModel.getMessage(this@BlockedContactsActivity))
            button(R.string.continue_2) { viewModel.unblock() }
            cancelButton()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        binding = ActivityBlockedContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerView.adapter = adapter

        viewModel.subscribe(this)
            .observe(this) { state ->
                adapter.submitList(state.items)
                binding.emptyStateMessageTextView.isVisible = state.emptyStateMessageTextViewVisible
                binding.nonEmptyStateGroup.isVisible = state.nonEmptyStateGroupVisible
                binding.unblockButton.isEnabled = state.unblockButtonEnabled
            }

        binding.unblockButton.setOnClickListener { unblock() }

    }
}
