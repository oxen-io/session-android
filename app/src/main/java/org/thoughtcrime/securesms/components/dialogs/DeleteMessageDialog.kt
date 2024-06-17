package org.thoughtcrime.securesms.components.dialogs

import android.content.Context
import android.util.TypedValue
import androidx.annotation.ColorInt
import network.loki.messenger.R
import org.thoughtcrime.securesms.showSessionDialog


class DeleteMessageDialog {
    companion object {
        @JvmStatic
        fun show(context: Context, recordCount: Int, defaultToEveryone: Boolean, doDelete: Runnable) {
            var deleteForEveryone = defaultToEveryone

            val typedValue = TypedValue()
            val theme = context.theme
            theme.resolveAttribute(R.attr.danger, typedValue, true)
            @ColorInt val deleteColor = typedValue.data

            context.showSessionDialog {
                title("This is for Deleting with options")
                text(
                    context.resources.getQuantityString(
                        R.plurals.MediaOverviewActivity_Media_delete_confirm_message,
                        recordCount,
                        recordCount
                    )
                )
                singleChoiceItems(
                    options = context.resources.getStringArray(R.array.notify_types),
                    currentSelected = if (defaultToEveryone) 1 else 0, // some cases require the second option, "delete for everyone", to be the default selected
                    dismissOnRadioSelect = false
                ) { index ->
                    deleteForEveryone = (index == 1) // we delete for everyone if the selected index is 1
                }
                button(
                    text = R.string.delete,
                    textColor = deleteColor
                ) { doDelete.run() }
                cancelButton()
            }
        }
    }
}
