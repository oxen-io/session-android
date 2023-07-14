package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import network.loki.messenger.R
import org.thoughtcrime.securesms.createSessionDialog

/** Shown if the user is about to send their recovery phrase to someone. */
class SendSeedDialog(private val proceed: (() -> Unit)? = null) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = createSessionDialog {
        title(R.string.dialog_send_seed_title)
        text(R.string.dialog_send_seed_explanation)
        button(R.string.dialog_send_seed_send_button_title) { send() }
        cancelButton()
    }

    private fun send() {
        proceed?.invoke()
        dismiss()
    }
}
