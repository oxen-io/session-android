package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import network.loki.messenger.R
import network.loki.messenger.databinding.DialogJoinOpenGroupBinding
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.utilities.OpenGroupUrlParser
import org.session.libsignal.utilities.ThreadUtils
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities

/** Shown upon tapping an open group invitation. */
class JoinOpenGroupDialog(private val name: String, private val url: String) : BaseDialog() {

    override fun setContentView(builder: AlertDialog.Builder) {
        val binding = DialogJoinOpenGroupBinding.inflate(LayoutInflater.from(requireContext()))
        val title = resources.getString(R.string.dialog_join_open_group_title, name)
        binding.joinOpenGroupTitleTextView.text = title
        val explanation = resources.getString(R.string.dialog_join_open_group_explanation, name)
        val spannable = SpannableStringBuilder(explanation)
        val startIndex = explanation.indexOf(name)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startIndex, startIndex + name.count(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.joinOpenGroupExplanationTextView.text = spannable
        binding.cancelButton.setOnClickListener { dismiss() }
        binding.joinButton.setOnClickListener { join() }
        builder.setView(binding.root)
    }

    private fun join() {
        val openGroup = OpenGroupUrlParser.parseUrl(url)
        val activity = requireContext() as AppCompatActivity
        ThreadUtils.queue {
            try {
                OpenGroupManager.add(openGroup.server, openGroup.room, openGroup.serverPublicKey, activity)
                MessagingModuleConfiguration.shared.storage.onOpenGroupAdded(openGroup.server)
                ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(activity)
            } catch (e: Exception) {
                Toast.makeText(activity, R.string.activity_join_public_chat_error, Toast.LENGTH_SHORT).show()
            }
        }
        dismiss()
    }
}