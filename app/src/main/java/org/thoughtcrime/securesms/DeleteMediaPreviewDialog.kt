package org.thoughtcrime.securesms

import android.content.Context
import network.loki.messenger.R

class DeleteMediaPreviewDialog {
    companion object {
        @JvmStatic
        fun show(context: Context, doDelete: Runnable) {
            context.showSessionDialog {
                iconAttribute(R.attr.dialog_alert_icon)
                title(R.string.deleteMessage)
                text(R.string.deleteMessageDescriptionEveryone)
                button(R.string.delete) { doDelete.run() }
                cancelButton()
            }
        }
    }
}