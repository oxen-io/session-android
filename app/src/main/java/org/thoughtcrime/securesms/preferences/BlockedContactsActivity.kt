package org.thoughtcrime.securesms.preferences

import android.app.AlertDialog
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityBlockedContactsBinding
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity

@AndroidEntryPoint
class BlockedContactsActivity: PassphraseRequiredActionBarActivity() {

    lateinit var binding: ActivityBlockedContactsBinding

    val viewModel: BlockedContactsViewModel by viewModels()

    val adapter: BlockedContactsAdapter by lazy { BlockedContactsAdapter(viewModel) }

    fun unblock() {
        // show dialog
        val title = viewModel.getTitle(this)

        val message = viewModel.getMessage(this)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.continue_2) { _, _ -> viewModel.unblock() }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .show()
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
    