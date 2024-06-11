package org.thoughtcrime.securesms.components.dialogs

import android.content.Context
import network.loki.messenger.R
import org.thoughtcrime.securesms.showSessionDialog

class DeleteMessageDeviceOnlyDialog {
    companion object {
        @JvmStatic
        fun show(context: Context, recordCount: Int, doDelete: Runnable) =
            context.showSessionDialog {
                iconAttribute(R.attr.dialog_alert_icon)
                title("This is for Deleting device only")
                text(context.resources.getQuantityString(
                        R.plurals.MediaOverviewActivity_Media_delete_confirm_message,
                        recordCount,
                        recordCount
                    ))
                button(R.string.delete) { doDelete.run() }
                cancelButton()
            }
    }
}
