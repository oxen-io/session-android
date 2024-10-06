package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import network.loki.messenger.R
import org.session.libsession.utilities.NonTranslatableStringConstants.APP_NAME
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.createSessionDialog
import org.thoughtcrime.securesms.ui.getSubbedCharSequence

/** Shown the first time the user inputs a URL that could generate a link preview, to
 * let them know that Session offers the ability to send and receive link previews. */
class LinkPreviewDialog(private val onEnabled: () -> Unit) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = createSessionDialog {
        title(R.string.linkPreviewsEnable)
        val txt = context.getSubbedCharSequence(R.string.linkPreviewsFirstDescription, APP_NAME_KEY to APP_NAME)
        text(txt)
        dangerButton(R.string.enable) { enable()  }
        cancelButton     { dismiss() }
    }

    private fun enable() {
        TextSecurePreferences.setLinkPreviewsEnabled(requireContext(), true)
        dismiss()
        onEnabled()
    }
}
