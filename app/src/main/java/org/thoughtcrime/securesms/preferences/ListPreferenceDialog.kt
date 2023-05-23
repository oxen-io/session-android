package org.thoughtcrime.securesms.preferences

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import network.loki.messenger.databinding.DialogListPreferenceBinding

fun listPreferenceDialog(
    context: Context,
    listPreference: ListPreference,
    dialogListener: () -> Unit
) : AlertDialog {

    val builder = AlertDialog.Builder(context)

    val binding = DialogListPreferenceBinding.inflate(LayoutInflater.from(context))
    binding.titleTextView.text = listPreference.dialogTitle
    binding.messageTextView.text = listPreference.dialogMessage

    builder.setView(binding.root)

    val dialog = builder.show()

    val valueIndex = listPreference.findIndexOfValue(listPreference.value)
    RadioOptionAdapter(valueIndex) {
        listPreference.value = it.value
        dialog.dismiss()
        dialogListener()
    }
        .apply {
            listPreference.entryValues.zip(listPreference.entries) { value, title ->
                RadioOption(value.toString(), title.toString())
            }.let(this::submitList)
        }
        .let { binding.recyclerView.adapter = it }

    binding.closeButton.setOnClickListener { dialog.dismiss() }

    return dialog
}
