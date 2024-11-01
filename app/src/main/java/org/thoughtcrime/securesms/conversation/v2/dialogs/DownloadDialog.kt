package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.StringSubstitutionConstants.CONVERSATION_NAME_KEY
import org.thoughtcrime.securesms.createSessionDialog
import org.thoughtcrime.securesms.database.SessionContactDatabase
import org.thoughtcrime.securesms.util.createAndStartAttachmentDownload
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
        title(getString(R.string.attachmentsAutoDownloadModalTitle))

        val explanation = Phrase.from(context, R.string.attachmentsAutoDownloadModalDescription)
            .put(CONVERSATION_NAME_KEY, threadRecipient.toShortString())
            .format()
        text(explanation)

        button(R.string.download, R.string.AccessibilityId_download) {
            setAutoDownload()
        }

        cancelButton { dismiss() }
    }

    private fun setAutoDownload() {
        storage.setAutoDownloadAttachments(threadRecipient, true)
        JobQueue.shared.createAndStartAttachmentDownload(databaseAttachment)
    }
}
