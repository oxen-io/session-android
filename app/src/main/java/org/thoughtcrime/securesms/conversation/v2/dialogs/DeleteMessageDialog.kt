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
 * Shown when deleting a message that can be removed both locally and for everyone
 *
 * @param messageCount The number of messages to be deleted.
 * @param defaultToEveryone Whether the dialog should default to deleting for everyone.
 * @param onDeleteDeviceOnly Callback to be executed when the user chooses to delete only on their device.
 * @param onDeleteForEveryone Callback to be executed when the user chooses to delete for everyone.
 * @param onCancel Callback to be executed when cancelling the dialog.
 */
class DeleteMessageDialog(
    private val messageCount: Int,
    private val defaultToEveryone: Boolean,
    private val onDeleteDeviceOnly: () -> Unit,
    private val onDeleteForEveryone: () -> Unit,
    private val onCancel: () -> Unit
) : DialogFragment() {

    // tracking the user choice from the radio buttons
    private var deleteForEveryone = defaultToEveryone

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = createSessionDialog {
        val typedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(R.attr.danger, typedValue, true)
        @ColorInt val deleteColor = typedValue.data

        title(resources.getQuantityString(R.plurals.deleteMessage, messageCount, messageCount))
        text(resources.getString(R.string.deleteMessageConfirm)) //todo DELETION we need the plural version of this here, which currently is not set up in strings
        singleChoiceItems(
            options = deleteOptions.map { it.label },
            currentSelected = if (defaultToEveryone) 1 else 0, // some cases require the second option, "delete for everyone", to be the default selected
            dismissOnRadioSelect = false
        ) { index ->
            deleteForEveryone = (deleteOptions[index] is DeleteOption.DeleteForEveryone) // we delete for everyone if the selected index is 1
        }
        button(
            text = R.string.delete,
            textColor = deleteColor,
            listener = {
                if (deleteForEveryone) {
                    onDeleteForEveryone()
                } else {
                    onDeleteDeviceOnly()
                }
            }
        )
        cancelButton(onCancel)
    }

    private val deleteOptions: List<DeleteOption> by lazy {
        listOf(
            DeleteOption.DeleteDeviceOnly(requireContext()), DeleteOption.DeleteForEveryone(requireContext())
        )
    }

    private sealed class DeleteOption(
        open val label: String
    ){
        data class DeleteDeviceOnly(
            val context: Context,
            override val label: String = context.getString(R.string.deleteMessageDeviceOnly),
        ): DeleteOption(label)

        data class DeleteForEveryone(
            val context: Context,
            override val label: String =  context.getString(R.string.deleteMessageEveryone),
        ): DeleteOption(label)
    }
}
