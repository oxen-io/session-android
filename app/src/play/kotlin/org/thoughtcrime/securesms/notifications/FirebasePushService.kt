package org.thoughtcrime.securesms.notifications

import android.Manifest.permission.POST_NOTIFICATIONS
import android.os.Build

import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import javax.inject.Inject

private const val TAG = "FirebasePushNotificationService"
@AndroidEntryPoint
class FirebasePushService : FirebaseMessagingService() {

    @Inject lateinit var prefs: TextSecurePreferences
    @Inject lateinit var pushReceiver: PushReceiver
    @Inject lateinit var pushRegistry: PushRegistry

    // ACL Note: Not seeing this getting hit
    override fun onNewToken(token: String) {

        Log.d("[ACL]", "Hit `onNewToken: $token - getPrefsToken is: ${prefs.getPushToken()} - same?: ${token == prefs.getPushToken()}")

        if (token == prefs.getPushToken()) return

        pushRegistry.register(token)
    }

    // ACL Note: The guts of this are the `enc_payload` which is just a bunch of noise.
    private fun getMessageDataString(message: RemoteMessage): String {
        return message.data.map { "${it.key}: ${it.value}" }.joinToString(", ")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "Received a push notification.")

        Log.d("[ACL]", "Message details: ${getMessageDataString(message)}")

        var nmc = NotificationManagerCompat.from(this)
        val notificationsEnabled = nmc.areNotificationsEnabled()
        Log.d("[ACL]", "Notifications are enabled?: $notificationsEnabled")

        // Need Android 13 / API 33 to use the `POST_NOTIFICATIONS` permission.
        // See: https://developer.android.com/develop/ui/views/notifications/notification-permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val postNotificationsPermissionStatus = ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS)
            Log.d("[ACL]", "POST_NOTIFICATIONS permission status: $postNotificationsPermissionStatus")
        }

        //message.toString()


        pushReceiver.onPush(message.data)
    }

    override fun onDeletedMessages() {
        Log.d(TAG, "Called onDeletedMessages.")
        pushRegistry.refresh(true)
    }
}
