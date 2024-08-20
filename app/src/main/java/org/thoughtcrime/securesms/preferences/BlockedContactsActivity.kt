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

    // Method to show a sequence of toasts one after the other
    fun showToastSequence(toastStrings: List<String>, toastLengthSetting: Int,context: Context) {
        val handler = Handler(Looper.getMainLooper())

        val delayStepMilliseconds = when (toastLengthSetting) {
            Toast.LENGTH_SHORT -> 2000L
            Toast.LENGTH_LONG -> 3500L
            else -> {
                Log.w("BlockContactsActivity", "Invalid toast length setting - using Toast.LENGTH_SHORT")
                2000L
            }
        }

        var delayMilliseconds = 0L
        toastStrings.forEach { message ->
            handler.postDelayed( { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }, delayMilliseconds)
            delayMilliseconds += delayStepMilliseconds // Increment delay by the duration of a Toast message
        }
    }

    fun unblock() {
        showSessionDialog {
            title(viewModel.getTitle(this@BlockedContactsActivity))


            val contactsToUnblock = viewModel.state.selectedItems

            // Get the names of each person to unblock for later user in the toast(s)
            // Note: We must do this before `viewModel.unblock`
            //val contactsToUnblockNames = contactsToUnblock.map { it.name }

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

            button(R.string.theContinue) {
                // Show individual toasts for each unblocked user (we don't have suitable strings to do it as a single toast)
                val contactsToUnblockNames = contactsToUnblock.map { it.name }
                val toastStrings = mutableListOf<String>()
                for (name in contactsToUnblockNames) {
                    toastStrings.add(Phrase.from(context, R.string.blockUnblockedUser).put(NAME_KEY, name).format().toString())
                }
                showToastSequence(toastStrings, Toast.LENGTH_SHORT, this@BlockedContactsActivity)

                viewModel.unblock()
            }
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
