package org.thoughtcrime.securesms.permissions

import android.content.Context
import network.loki.messenger.R
import org.thoughtcrime.securesms.showSessionDialog

class SettingsDialog {
    companion object {
        @JvmStatic
        fun show(context: Context, message: String) {
            context.showSessionDialog {
                title(R.string.Permissions_permission_required)
                text(message)
                button(R.string.Permissions_continue, R.string.AccessibilityId_continue) {
                    context.startActivity(Permissions.getApplicationSettingsIntent(context))
                }
                cancelButton()
            }
        }
    }
}
