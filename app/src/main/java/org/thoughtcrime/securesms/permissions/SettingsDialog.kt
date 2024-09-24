package org.thoughtcrime.securesms.permissions

import android.content.Context
import network.loki.messenger.R
import org.thoughtcrime.securesms.showSessionDialog

class SettingsDialog {
    companion object {
        @JvmStatic
        fun show(context: Context, message: String) {
            context.showSessionDialog {
                title(R.string.permissionsRequired)
                text(message)
                button(R.string.sessionSettings, R.string.AccessibilityId_sessionSettings) {
                    context.startActivity(Permissions.getApplicationSettingsIntent(context))
                }
                cancelButton()
            }
        }
    }
}
