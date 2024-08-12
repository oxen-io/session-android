package org.thoughtcrime.securesms.preferences

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.ACCOUNT_ID_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.DOWNLOAD_URL_KEY
import org.session.libsession.utilities.TextSecurePreferences

fun Context.sendInvitationToUseSession() {

    val DOWNLOAD_URL = "https://getsession.org/download"

    val txt = Phrase.from(getString(R.string.accountIdShare))
        .put(APP_NAME_KEY, getString(R.string.app_name))
        .put(ACCOUNT_ID_KEY, TextSecurePreferences.getLocalNumber(this@sendInvitationToUseSession))
        .put(DOWNLOAD_URL_KEY, DOWNLOAD_URL)
        .format().toString()

    Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(
            Intent.EXTRA_TEXT,
            txt
        )
        type = "text/plain"
    }.let { Intent.createChooser(it, getString(R.string.sessionInviteAFriend)) }
        .let(::startActivity)
}

fun Context.copyPublicKey() {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Account ID", TextSecurePreferences.getLocalNumber(this))
    clipboard.setPrimaryClip(clip)
    Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
}
