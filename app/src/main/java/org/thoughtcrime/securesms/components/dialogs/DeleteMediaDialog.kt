package org.thoughtcrime.securesms.components.dialogs

import android.content.Context
import network.loki.messenger.R
import org.thoughtcrime.securesms.showSessionDialog

class DeleteMediaDialog {
    companion object {
        @JvmStatic
        fun show(context: Context, recordCount: Int, doDelete: Runnable) = context.showSessionDialog {
            iconAttribute(R.attr.dialog_alert_icon)
            title(context.resources.getQuantityString(R.plurals.deleteMessage, recordCount, recordCount))
            text(context.resources.getString(R.string.deleteMessageDescriptionEveryone))
            dangerButton(R.string.delete) { doDelete.run() }
            cancelButton()
        }
    }
}
