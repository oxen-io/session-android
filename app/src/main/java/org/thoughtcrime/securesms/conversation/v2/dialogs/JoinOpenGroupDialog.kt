package org.thoughtcrime.securesms.conversation.v2.dialogs

import org.thoughtcrime.securesms.createSessionDialog
import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.utilities.OpenGroupUrlParser
import org.session.libsession.utilities.StringSubstitutionConstants.COMMUNITY_NAME_KEY
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.ThreadUtils
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities

/** Shown upon tapping an open group invitation. */
class JoinOpenGroupDialog(private val name: String, private val url: String) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = createSessionDialog {
        title(resources.getString(R.string.communityJoin))
        val explanation = Phrase.from(context, R.string.communityJoinDescription).put(COMMUNITY_NAME_KEY, name).format()
        val spannable = SpannableStringBuilder(explanation)
        var startIndex = explanation.indexOf(name)
        if (startIndex < 0) {
            Log.w("JoinOpenGroupDialog", "Could not find $name in explanation dialog: $explanation")
            startIndex = 0 // Limit the startIndex to zero if not found (will be -1) to prevent a crash
        }
        spannable.setSpan(StyleSpan(Typeface.BOLD), startIndex, startIndex + name.count(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        text(spannable)
        cancelButton { dismiss() }
        button(R.string.join) { join() }
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
                Toast.makeText(activity, R.string.communityErrorDescription, Toast.LENGTH_SHORT).show()
            }
        }
        dismiss()
    }
}
