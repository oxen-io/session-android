package org.thoughtcrime.securesms

import android.content.Context
import network.loki.messenger.R

class DeleteMediaDialog {
    companion object {
        @JvmStatic
        fun show(context: Context, recordCount: Int, doDelete: Runnable) = context.showSessionDialog {
            iconAttribute(R.attr.dialog_alert_icon)
            title(
                context.resources.getQuantityString(
                    R.plurals.MediaOverviewActivity_Media_delete_confirm_title,
                    recordCount,
                    recordCount
                )
            )
            text(
                context.resources.getQuantityString(R.plurals.MediaOverviewActivity_Media_delete_confirm_message,
                    recordCount,
                    recordCount
                )
            )
            button(R.string.delete) { doDelete.run() }
            cancelButton()
        }
    }
}
