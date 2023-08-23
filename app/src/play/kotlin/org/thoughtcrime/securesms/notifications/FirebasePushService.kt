package org.thoughtcrime.securesms.notifications

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

    override fun onNewToken(token: String) {
        if (token == prefs.getPushToken()) return

        pushRegistry.register(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "Received a push notification.")
        pushReceiver.onPush(message.data)
    }

    override fun onDeletedMessages() {
        Log.d(TAG, "Called onDeletedMessages.")
        pushRegistry.refresh(true)
    }
}
