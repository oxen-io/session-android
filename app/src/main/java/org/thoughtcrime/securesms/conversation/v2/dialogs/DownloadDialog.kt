package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.fragment.app.DialogFragment
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.jobs.AttachmentDownloadJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.createSessionDialog
import org.thoughtcrime.securesms.database.SessionContactDatabase
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import javax.inject.Inject

/** Shown when receiving media from a contact for the first time, to confirm that
 * they are to be trusted and files sent by them are to be downloaded. */
@AndroidEntryPoint
class DownloadDialog(private val recipient: Recipient) : DialogFragment() {

    @Inject lateinit var contactDB: SessionContactDatabase

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = createSessionDialog {
        val sessionID = recipient.address.toString()
        val contact = contactDB.getContactWithSessionID(sessionID)
        val name = contact?.displayName(Contact.ContactContext.REGULAR) ?: sessionID
        title(resources.getString(R.string.dialog_download_title, name))

        val explanation = resources.getString(R.string.dialog_download_explanation, name)
        val spannable = SpannableStringBuilder(explanation)
        val startIndex = explanation.indexOf(name)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startIndex, startIndex + name.count(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        text(spannable)

        button(R.string.dialog_download_button_title, R.string.AccessibilityId_download_media) { trust() }
        cancelButton { dismiss() }
    }

    private fun trust() {
        val sessionID = recipient.address.toString()
        val contact = contactDB.getContactWithSessionID(sessionID) ?: return
        val threadID = DatabaseComponent.get(requireContext()).threadDatabase().getThreadIdIfExistsFor(recipient)
        contactDB.setContactIsTrusted(contact, true, threadID)
        JobQueue.shared.resumePendingJobs(AttachmentDownloadJob.KEY)
        dismiss()
    }
}
