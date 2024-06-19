package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.app.Dialog
import android.os.Bundle
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.fragment.app.DialogFragment
import network.loki.messenger.R
import org.thoughtcrime.securesms.createSessionDialog

/**
 * Shown when deleting a message can only be deleted locally
 *
 * @param messageCount The number of messages to be deleted.
 * @param onDeleteDeviceOnly Callback to be executed when the user chooses to delete only on their device.
 * @param onCancel Callback to be executed when cancelling the dialog.
 */
class DeleteMessageDeviceOnlyDialog(
    private val messageCount: Int,
    private val onDeleteDeviceOnly: () -> Unit,
    private val onCancel: () -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = createSessionDialog {
        val typedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(R.attr.danger, typedValue, true)
        @ColorInt val deleteColor = typedValue.data

        title("[UPDATE THIS!] Delete message") //todo DELETION update once we have strings
        text("[UPDATE THIS!] This will delete this message device only") //todo DELETION update once we have strings
        button(
            text = R.string.delete,
            textColor = deleteColor,
            listener = {
                onDeleteDeviceOnly()
            }
        )
        cancelButton(onCancel)
    }
}
