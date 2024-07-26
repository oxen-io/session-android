package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import network.loki.messenger.R
import org.thoughtcrime.securesms.createSessionDialog

/** Shown if the user is about to send their recovery phrase to someone. */
class SendSeedDialog(private val proceed: (() -> Unit)? = null) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = createSessionDialog {
        title(R.string.warning)
        text(R.string.recoveryPasswordWarningSendDescription)
        button(R.string.send) { send() }
        cancelButton()
    }

    private fun send() {
        proceed?.invoke()
        dismiss()
    }
}
