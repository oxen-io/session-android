package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import network.loki.messenger.R
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.utilities.OpenGroupUrlParser
import org.session.libsignal.utilities.ThreadUtils
import org.thoughtcrime.securesms.createSessionDialog
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities

/** Shown upon tapping an open group invitation. */
class JoinOpenGroupDialog(private val name: String, private val url: String) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = createSessionDialog {
        title(resources.getString(R.string.dialog_join_open_group_title, name))
        val explanation = resources.getString(R.string.dialog_join_open_group_explanation, name)
        val spannable = SpannableStringBuilder(explanation)
        val startIndex = explanation.indexOf(name)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startIndex, startIndex + name.count(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        text(spannable)
        cancelButton { dismiss() }
        button(R.string.open_group_invitation_view__join_accessibility_description) { join() }
    }

    private fun join() {
        val openGroup = OpenGroupUrlParser.parseUrl(url)
        val activity = requireActivity()
        ThreadUtils.queue {
            try {
                openGroup.apply { OpenGroupManager.add(server, room, serverPublicKey, activity) }
                MessagingModuleConfiguration.shared.storage.onOpenGroupAdded(openGroup.server, openGroup.room)
                ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(activity)
            } catch (e: Exception) {
                Toast.makeText(activity, R.string.activity_join_public_chat_error, Toast.LENGTH_SHORT).show()
            }
        }
        dismiss()
    }
}
