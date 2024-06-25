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
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.createSessionDialog
import org.thoughtcrime.securesms.database.SessionContactDatabase
import javax.inject.Inject

/** Shown when receiving media from a contact for the first time, to confirm that
 * they are to be trusted and files sent by them are to be downloaded. */
@AndroidEntryPoint
class AutoDownloadDialog(private val threadRecipient: Recipient,
                     private val databaseAttachment: DatabaseAttachment
) : DialogFragment() {

    @Inject lateinit var storage: StorageProtocol
    @Inject lateinit var contactDB: SessionContactDatabase

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = createSessionDialog {
        val threadId = storage.getThreadId(threadRecipient) ?: run {
            dismiss()
            return@createSessionDialog
        }

        val displayName = when {
            threadRecipient.isCommunityRecipient -> storage.getOpenGroup(threadId)?.name ?: "UNKNOWN"
            threadRecipient.isLegacyClosedGroupRecipient -> storage.getGroup(threadRecipient.address.toGroupString())?.title ?: "UNKNOWN"
            threadRecipient.isClosedGroupV2Recipient -> threadRecipient.name ?: "UNKNOWN"
            else -> storage.getContactWithSessionID(threadRecipient.address.serialize())?.displayName(Contact.ContactContext.REGULAR) ?: "UNKNOWN"
        }
        title(resources.getString(R.string.dialog_auto_download_title))

        val explanation = resources.getString(R.string.dialog_auto_download_explanation, displayName)
        val spannable = SpannableStringBuilder(explanation)
        val startIndex = explanation.indexOf(displayName)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startIndex, startIndex + displayName.count(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        text(spannable)

        button(R.string.dialog_download_button_title, R.string.AccessibilityId_download_media) {
            setAutoDownload(true)
        }
        cancelButton {
            setAutoDownload(false)
        }
    }

    private fun setAutoDownload(shouldDownload: Boolean) {
        storage.setAutoDownloadAttachments(threadRecipient, shouldDownload)
    }
}
