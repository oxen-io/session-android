package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.fragment.app.DialogFragment
import network.loki.messenger.R
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.createSessionDialog
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.ui.getSubbedCharSequence

/** Shown upon sending a message to a user that's blocked. */
class BlockedDialog(private val recipient: Recipient, private val context: Context) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = createSessionDialog {
        val contactDB = DatabaseComponent.get(requireContext()).sessionContactDatabase()
        val accountID = recipient.address.toString()
        val contact = contactDB.getContactWithAccountID(accountID)
        val name = contact?.displayName(Contact.ContactContext.REGULAR) ?: accountID

        val explanationCS = context.getSubbedCharSequence(R.string.blockUnblockName, NAME_KEY to name)
        val spannable = SpannableStringBuilder(explanationCS)
        val startIndex = explanationCS.indexOf(name)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startIndex, startIndex + name.count(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        title(resources.getString(R.string.blockUnblock))
        text(spannable)
        dangerButton(R.string.blockUnblock, R.string.AccessibilityId_unblockConfirm) { unblock() }
        cancelButton { dismiss() }
    }

    private fun unblock() {
        MessagingModuleConfiguration.shared.storage.setBlocked(listOf(recipient), false)
        dismiss()
    }
}
