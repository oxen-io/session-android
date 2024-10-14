package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.fragment.app.DialogFragment
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.jobs.AttachmentDownloadJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.StringSubstitutionConstants.CONVERSATION_NAME_KEY
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
        val accountID = recipient.address.toString()
        val contact = contactDB.getContactWithAccountID(accountID)
        val name = contact?.displayName(Contact.ContactContext.REGULAR) ?: accountID

        title(getString(R.string.attachmentsAutoDownloadModalTitle))

        val explanation = Phrase.from(context, R.string.attachmentsAutoDownloadModalDescription)
            .put(CONVERSATION_NAME_KEY, recipient.toShortString())
            .format()
        text(explanation)

        button(R.string.download, R.string.AccessibilityId_download) { trust() }
        cancelButton { dismiss() }
    }

    private fun trust() {
        val accountID = recipient.address.toString()
        val contact = contactDB.getContactWithAccountID(accountID) ?: return
        val threadID = DatabaseComponent.get(requireContext()).threadDatabase().getThreadIdIfExistsFor(recipient)
        contactDB.setContactIsTrusted(contact, true, threadID)
        JobQueue.shared.resumePendingJobs(AttachmentDownloadJob.KEY)
        dismiss()
    }
}
