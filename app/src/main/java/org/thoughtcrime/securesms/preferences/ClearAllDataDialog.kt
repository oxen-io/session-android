package org.thoughtcrime.securesms.preferences

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
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

enum class DeletionScope {
    DeleteLocalDataOnly,
    DeleteBothLocalAndNetworkData
}

class ClearAllDataDialog : DialogFragment() {
    private lateinit var binding: DialogClearAllDataBinding

    enum class Steps {
        INFO_PROMPT,
        NETWORK_PROMPT,
        DELETING,
        RETRY_LOCAL_DELETE_ONLY_PROMPT
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
                }
            }
            binding.recyclerView.isGone = step == Steps.NETWORK_PROMPT
            binding.cancelButton.isVisible = !isLoading
            binding.clearAllDataButton.isVisible = !isLoading
            binding.progressBar.isVisible = isLoading

            it.setCanceledOnTouchOutside(!isLoading)
            isCancelable = !isLoading
        }
    }

    private fun clearAllData(deletionScope: DeletionScope) {
        clearJob = lifecycleScope.launch(Dispatchers.IO) {
            val previousStep = step
            withContext(Dispatchers.Main) { step = Steps.DELETING }

            if (deletionScope == DeletionScope.DeleteLocalDataOnly) {
                try {
                    ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(requireContext()).get()
                } catch (e: Exception) {
                    Log.e("Loki", "Failed to force sync deleting data", e)
                    Toast.makeText(requireContext(), R.string.errorUnknown, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val success = ApplicationContext.getInstance(context).clearAllData(false)
                withContext(Dispatchers.Main) { dismiss() }

                if (!success) {
                    Toast.makeText(requireContext(), R.string.errorUnknown, Toast.LENGTH_SHORT).show()
                }
            }
            else if (deletionScope == DeletionScope.DeleteBothLocalAndNetworkData) {

                val deletionResultMap: Map<String, Boolean>? = try {
                    val openGroups = DatabaseComponent.get(requireContext()).lokiThreadDatabase().getAllOpenGroups()
                    openGroups.map { it.value.server }.toSet().forEach { server ->
                        OpenGroupApi.deleteAllInboxMessages(server).get()
                    }
                    SnodeAPI.deleteAllMessages().get()
                } catch (e: Exception) {

                    null
                }

                // If one or more deletions failed..
                if (deletionResultMap == null || deletionResultMap.values.any { !it } || deletionResultMap.isEmpty()) {
                    Log.w("ACL", "Hit one or more deletions failed block")

                    withContext(Dispatchers.Main) { step = Steps.RETRY_LOCAL_DELETE_ONLY_PROMPT }
                    //withContext(Dispatchers.Main) { dismiss() }
                }
                else if (deletionResultMap.values.all { it }) {

                    Log.w("ACL", "Hit NOT failed block?!?!")

                    // Don't force sync because all the messages are deleted?
                    ApplicationContext.getInstance(context).clearAllData(false)
                    withContext(Dispatchers.Main) { dismiss() }
                }
            }
        }
    }
}