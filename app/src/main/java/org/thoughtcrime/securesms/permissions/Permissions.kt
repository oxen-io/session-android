package org.thoughtcrime.securesms.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import androidx.fragment.app.Fragment
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences


fun Fragment.requestMicrophonePermission(callback: (Boolean) -> Unit) {
    requireContext().requestMicrophonePermission(Permissions.with(this), callback)
}

fun Activity.requestMicrophonePermission(callback: (Boolean) -> Unit) {
    requestMicrophonePermission(Permissions.with(this), callback)
}

private fun Context.requestMicrophonePermission(
    permissions: Permissions.PermissionsBuilder,
    callback: (Boolean) -> Unit
) {
    permissions
        .request(Manifest.permission.RECORD_AUDIO)
        .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_requires_the_microphone_permission_in_order_to_send_audio_messages))
        .onAllGranted { callback(true) }
        .onAnyDenied { callback(false) }
        .execute()
}
