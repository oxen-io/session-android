package org.thoughtcrime.securesms

import android.content.Context
import network.loki.messenger.R

class DeleteMediaDialog {
    companion object {
        @JvmStatic
        fun show(context: Context, recordCount: Int, doDelete: Runnable) = context.showSessionDialog {
            iconAttribute(R.attr.dialog_alert_icon)
            title(context.resources.getQuantityString(R.plurals.deleteMessage, recordCount, recordCount))
            text(context.resources.getString(R.string.deleteMessageDescriptionEveryone))
            button(R.string.delete) { doDelete.run() }
            cancelButton()
        }
    }
}
