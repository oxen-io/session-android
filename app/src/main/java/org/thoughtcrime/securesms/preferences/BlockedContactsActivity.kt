package org.thoughtcrime.securesms.preferences

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityBlockedContactsBinding
import org.session.libsession.utilities.StringSubstitutionConstants.COUNT_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.showSessionDialog

@AndroidEntryPoint
class BlockedContactsActivity: PassphraseRequiredActionBarActivity() {

    lateinit var binding: ActivityBlockedContactsBinding

    val viewModel: BlockedContactsViewModel by viewModels()

    val adapter: BlockedContactsAdapter by lazy { BlockedContactsAdapter(viewModel) }

    private fun unblock() {
        showSessionDialog {
            title(viewModel.getTitle(this@BlockedContactsActivity))

            val contactsToUnblock = viewModel.state.selectedItems
            val numContactsToUnblock = contactsToUnblock.size
            val txt = when (numContactsToUnblock) {
                // Note: We do not have to handle 0 because if no contacts are chosen then the unblock button is deactivated
                1 -> Phrase.from(context, R.string.blockUnblockName)
                        .put(NAME_KEY, contactsToUnblock.elementAt(0).name)
                        .format().toString()
                2 -> Phrase.from(context, R.string.blockUnblockNameTwo)
                        .put(NAME_KEY, contactsToUnblock.elementAt(0).name)
                        .format().toString()
                else -> {
                    val othersCount = contactsToUnblock.size - 1
                    Phrase.from(context, R.string.blockUnblockNameMultiple)
                        .put(NAME_KEY, contactsToUnblock.elementAt(0).name)
                        .put(COUNT_KEY, othersCount)
                        .format().toString()
                }
            }
            text(txt)

            dangerButton(R.string.blockUnblock, R.string.AccessibilityId_unblockConfirm) { viewModel.unblock() }
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
