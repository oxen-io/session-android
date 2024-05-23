package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.createSessionDialog

/** Shown the first time the user inputs a URL that could generate a link preview, to
 * let them know that Session offers the ability to send and receive link previews. */
class LinkPreviewDialog(private val onEnabled: () -> Unit) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = createSessionDialog {
        title(R.string.linkPreviewsEnable)
        text(R.string.linkPreviewsFirstDescription)
        button(R.string.enable) { enable() }
        cancelButton { dismiss() }
    }

    private fun enable() {
        TextSecurePreferences.setLinkPreviewsEnabled(requireContext(), true)
        dismiss()
        onEnabled()
    }
}
