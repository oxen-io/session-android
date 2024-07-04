package org.thoughtcrime.securesms.preferences

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Main
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
    private val TAG = "ClearAllDataDialog"

    private lateinit var binding: DialogClearAllDataBinding

    private enum class Steps {
        INFO_PROMPT,
        NETWORK_PROMPT,
        DELETING,
        RETRY_LOCAL_DELETE_ONLY_PROMPT
    }

    // Rather than passing a bool around we'll use an enum to clarify our intent
    private enum class DeletionScope {
        DeleteLocalDataOnly,
        DeleteBothLocalAndNetworkData
    }

    private var clearJob: Job? = null

    private var step = Steps.INFO_PROMPT
        set(value) {
            field = value
            updateUI()
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = createSessionDialog {
        view(createView())
    }

    private fun createView(): View {
        binding = DialogClearAllDataBinding.inflate(LayoutInflater.from(requireContext()))
        val device = radioOption("deviceOnly", R.string.clearDeviceOnly)
        val network = radioOption("deviceAndNetwork", R.string.clearDeviceAndNetwork)
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
            when (step) {
                Steps.INFO_PROMPT -> if (selectedOption == network) {
                    step = Steps.NETWORK_PROMPT
                } else {
                    clearAllData(DeletionScope.DeleteLocalDataOnly)
                }
                Steps.NETWORK_PROMPT -> clearAllData(DeletionScope.DeleteBothLocalAndNetworkData)
                Steps.DELETING -> { /* do nothing intentionally */ }
                Steps.RETRY_LOCAL_DELETE_ONLY_PROMPT -> clearAllData(DeletionScope.DeleteLocalDataOnly)
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
                Steps.RETRY_LOCAL_DELETE_ONLY_PROMPT -> {
                    binding.dialogDescriptionText.setText(R.string.clearDataErrorDescriptionGeneric)
                    binding.clearAllDataButton.text = getString(R.string.clearDevice)
                }
            }

            binding.recyclerView.isVisible = step == Steps.INFO_PROMPT
            binding.cancelButton.isVisible = !isLoading
            binding.clearAllDataButton.isVisible = !isLoading
            binding.progressBar.isVisible = isLoading

            it.setCanceledOnTouchOutside(!isLoading)
            isCancelable = !isLoading
        }
    }

    private suspend fun performDeleteLocalDataOnlyStep() {
        try {
            ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(requireContext()).get()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force sync when deleting data", e)
            withContext(Main) {
                Toast.makeText(ApplicationContext.getInstance(requireContext()), R.string.errorUnknown, Toast.LENGTH_LONG).show()
            }
            return
        }
        ApplicationContext.getInstance(context).clearAllData(false).let { success ->
            withContext(Main) {
                if (success) {
                    dismiss()
                } else {
                    Toast.makeText(ApplicationContext.getInstance(requireContext()), R.string.errorUnknown, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun clearAllData(deletionScope: DeletionScope) {
        step = Steps.DELETING

        clearJob = lifecycleScope.launch(Dispatchers.IO) {
            when (deletionScope) {
                DeletionScope.DeleteLocalDataOnly -> {
                    performDeleteLocalDataOnlyStep()
                }
                DeletionScope.DeleteBothLocalAndNetworkData -> {
                    val deletionResultMap: Map<String, Boolean>? = try {
                        val openGroups = DatabaseComponent.get(requireContext()).lokiThreadDatabase().getAllOpenGroups()
                        openGroups.map { it.value.server }.toSet().forEach { server ->
                            OpenGroupApi.deleteAllInboxMessages(server).get()
                        }
                        SnodeAPI.deleteAllMessages().get()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete network messages - offering user option to delete local data only.", e)
                        null
                    }

                    // If one or more deletions failed then inform the user and allow them to clear the device only if they wish..
                    if (deletionResultMap == null || deletionResultMap.values.any { !it } || deletionResultMap.isEmpty()) {
                        withContext(Main) { step = Steps.RETRY_LOCAL_DELETE_ONLY_PROMPT }
                    }
                    else if (deletionResultMap.values.all { it }) {
                        // ..otherwise if the network data deletion was successful proceed to delete the local data as well.
                        ApplicationContext.getInstance(context).clearAllData(false)
                        withContext(Main) { dismiss() }
                    }
                }
            }
        }
    }
}