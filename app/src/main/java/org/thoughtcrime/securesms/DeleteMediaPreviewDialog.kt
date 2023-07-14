package org.thoughtcrime.securesms

import android.content.Context
import network.loki.messenger.R

class DeleteMediaPreviewDialog {
    companion object {
        @JvmStatic
        fun show(context: Context, doDelete: Runnable) {
            context.showSessionDialog {
                iconAttribute(R.attr.dialog_alert_icon)
                title(R.string.MediaPreviewActivity_media_delete_confirmation_title)
                text(R.string.MediaPreviewActivity_media_delete_confirmation_message)
                button(R.string.delete) { doDelete.run() }
                cancelButton()
            }
        }
    }
}