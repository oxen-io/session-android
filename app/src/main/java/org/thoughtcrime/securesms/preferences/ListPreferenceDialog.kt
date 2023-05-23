package org.thoughtcrime.securesms.preferences

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import androidx.recyclerview.widget.DividerItemDecoration
import network.loki.messenger.databinding.DialogListPreferenceBinding
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog

fun listPreferenceDialog(
    context: Context,
    listPreference: ListPreference,
    dialogListener: () -> Unit
) : AlertDialog {

    val builder = AlertDialog.Builder(context)

    val binding = DialogListPreferenceBinding.inflate(LayoutInflater.from(context))
    binding.titleTextView.text = listPreference.dialogTitle
    binding.messageTextView.text = listPreference.dialogMessage

    val options = listPreference.entryValues.zip(listPreference.entries) { value, title ->
        RadioOption(value.toString(), title.toString())
    }
    val valueIndex = listPreference.findIndexOfValue(listPreference.value)
    builder.setView(binding.root)

    val dialog = builder.show()

    val optionAdapter = RadioOptionAdapter(valueIndex) {
        listPreference.value = it.value
        dialog.dismiss()
        dialogListener()
    }.apply { submitList(options) }

    binding.recyclerView.apply {
        adapter = optionAdapter
        addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        setHasFixedSize(true)
    }
    binding.closeButton.setOnClickListener { dialog.dismiss() }

    return dialog
}
