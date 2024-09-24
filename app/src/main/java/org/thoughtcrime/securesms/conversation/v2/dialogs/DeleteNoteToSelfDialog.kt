package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.fragment.app.DialogFragment
import network.loki.messenger.R
import org.thoughtcrime.securesms.createSessionDialog

/**
 * Shown when deleting a 'note to self'
 *
 * @param messageCount The number of messages to be deleted.
 * @param onDeleteDeviceOnly Callback to be executed when the user chooses to delete only on their device.
 * @param onDeleteAllDevices Callback to be executed when the user chooses to delete for everyone.
 * @param onCancel Callback to be executed when cancelling the dialog.
 */
class DeleteNoteToSelfDialog(
    private val messageCount: Int,
    private val onDeleteDeviceOnly: () -> Unit,
    private val onDeleteAllDevices: () -> Unit,
    private val onCancel: () -> Unit
) : DialogFragment() {

    // tracking the user choice from the radio buttons
    private var deleteOnAllDevices = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = createSessionDialog {
        val typedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(R.attr.danger, typedValue, true)
        @ColorInt val deleteColor = typedValue.data

        title(resources.getQuantityString(R.plurals.deleteMessage, messageCount, messageCount))
        text(resources.getString(R.string.deleteMessageConfirm)) //todo DELETION we need the plural version of this here, which currently is not set up in strings
        singleChoiceItems(
            options = deleteOptions.map { it.label },
            currentSelected = 0,
            dismissOnRadioSelect = false
        ) { index ->
            deleteOnAllDevices = (deleteOptions[index] is DeleteOption.DeleteOnAllMyDevices) // we delete for everyone if the selected index is 1
        }
        button(
            text = R.string.delete,
            textColor = deleteColor,
            listener = {
                if (deleteOnAllDevices) {
                    onDeleteAllDevices()
                } else {
                    onDeleteDeviceOnly()
                }
            }
        )
        cancelButton(onCancel)
    }

    private val deleteOptions: List<DeleteOption> = listOf(
        DeleteOption.DeleteDeviceOnly(requireContext()), DeleteOption.DeleteOnAllMyDevices(requireContext())
    )

    private sealed class DeleteOption(
        open val label: String
    ){
        data class DeleteDeviceOnly(
            val context: Context,
            override val label: String = context.getString(R.string.deleteMessageDeviceOnly),
        ): DeleteOption(label)

        data class DeleteOnAllMyDevices(
            val context: Context,
            override val label: String = context.getString(R.string.deleteMessageDevicesAll),
        ): DeleteOption(label)
    }
}
