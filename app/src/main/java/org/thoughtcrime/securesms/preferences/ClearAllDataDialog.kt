package org.thoughtcrime.securesms.preferences

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import network.loki.messenger.databinding.DialogClearAllDataBinding
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.snode.SnodeAPI
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.createSessionDialog
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities

class ClearAllDataDialog : DialogFragment() {
    private lateinit var binding: DialogClearAllDataBinding

    enum class Steps {
        INFO_PROMPT,
        NETWORK_PROMPT,
        DELETING
    }

    var clearJob: Job? = null

    var step = Steps.INFO_PROMPT
        set(value) {
            field = value
            updateUI()
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = createSessionDialog {
        view(createView())
    }

    private fun createView(): View {
        binding = DialogClearAllDataBinding.inflate(LayoutInflater.from(requireContext()))
        val device = radioOption("deviceOnly", R.string.dialog_clear_all_data_clear_device_only)
        val network = radioOption("deviceAndNetwork", R.string.dialog_clear_all_data_clear_device_and_network)
        var selectedOption: RadioOption<String> = device
        val optionAdapter = RadioOptionAdapter { selectedOption = it }
        binding.recyclerView.apply {
            itemAnimator = null
            adapter = optionAdapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            setHasFixedSize(true)
        }
        optionAdapter.submitList(listOf(device, network))
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
        binding.clearAllDataButton.setOnClickListener {
            when(step) {
                Steps.INFO_PROMPT -> if (selectedOption == network) {
                    step = Steps.NETWORK_PROMPT
                } else {
                    clearAllData(false)
                }
                Steps.NETWORK_PROMPT -> clearAllData(true)
                Steps.DELETING -> { /* do nothing intentionally */ }
            }
        }
        return binding.root
    }

    private fun updateUI() {
        dialog?.let {
            val isLoading = step == Steps.DELETING

            when (step) {
                Steps.INFO_PROMPT -> {
                    binding.dialogDescriptionText.setText(R.string.dialog_clear_all_data_message)
                }
                Steps.NETWORK_PROMPT -> {
                    binding.dialogDescriptionText.setText(R.string.dialog_clear_all_data_clear_device_and_network_confirmation)
                }
                Steps.DELETING -> { /* do nothing intentionally */ }
            }
            binding.recyclerView.isGone = step == Steps.NETWORK_PROMPT
            binding.cancelButton.isVisible = !isLoading
            binding.clearAllDataButton.isVisible = !isLoading
            binding.progressBar.isVisible = isLoading

            it.setCanceledOnTouchOutside(!isLoading)
            isCancelable = !isLoading
        }
    }

    private fun clearAllData(deleteNetworkMessages: Boolean) {
        clearJob = lifecycleScope.launch(Dispatchers.IO) {
            val previousStep = step
            withContext(Dispatchers.Main) {
                step = Steps.DELETING
            }

            if (!deleteNetworkMessages) {
                try {
                    ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(requireContext()).get()
                } catch (e: Exception) {
                    Log.e("Loki", "Failed to force sync", e)
                }
                ApplicationContext.getInstance(context).clearAllData(false)
                withContext(Dispatchers.Main) {
                    dismiss()
                }
            } else {
                // finish
                val result = try {
                    val openGroups = DatabaseComponent.get(requireContext()).lokiThreadDatabase().getAllOpenGroups()
                    openGroups.map { it.value.server }.toSet().forEach { server ->
                        OpenGroupApi.deleteAllInboxMessages(server).get()
                    }
                    SnodeAPI.deleteAllMessages().get()
                } catch (e: Exception) {
                    null
                }

                if (result == null || result.values.any { !it } || result.isEmpty()) {
                    // didn't succeed (at least one)
                    withContext(Dispatchers.Main) {
                        step = previousStep
                    }
                } else if (result.values.all { it }) {
                    // don't force sync because all the messages are deleted?
                    ApplicationContext.getInstance(context).clearAllData(false)
                    withContext(Dispatchers.Main) {
                        dismiss()
                    }
                }
            }
        }
    }
}