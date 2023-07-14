package org.thoughtcrime.securesms.conversation.v2.utilities

import android.content.Context
import network.loki.messenger.R
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.showSessionDialog

object NotificationUtils {
    fun showNotifyDialog(context: Context, thread: Recipient, notifyTypeHandler: (Int)->Unit) {
        context.showSessionDialog {
            title(R.string.RecipientPreferenceActivity_notification_settings)
            singleChoiceItems(
                context.resources.getStringArray(R.array.notify_types),
                thread.notifyType
            ) { notifyTypeHandler(it) }
        }
    }
}
