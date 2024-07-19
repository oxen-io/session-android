package org.thoughtcrime.securesms.preferences

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences

fun Context.sendInvitationToUseSession() {
    Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(
            Intent.EXTRA_TEXT,
            getString(
                R.string.accountIdShare,
                TextSecurePreferences.getLocalNumber(this@sendInvitationToUseSession)
            )
        )
        type = "text/plain"
    }.let { Intent.createChooser(it, getString(R.string.activity_settings_invite_button_title)) }
        .let(::startActivity)
}

fun Context.copyPublicKey() {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Account ID", TextSecurePreferences.getLocalNumber(this))
    clipboard.setPrimaryClip(clip)
    Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
}
