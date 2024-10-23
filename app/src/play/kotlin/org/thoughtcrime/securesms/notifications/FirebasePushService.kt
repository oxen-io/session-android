package org.thoughtcrime.securesms.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import org.session.libsession.messaging.notifications.TokenFetcher
import org.session.libsignal.utilities.Log
import javax.inject.Inject

private const val TAG = "FirebasePushNotificationService"

@AndroidEntryPoint
class FirebasePushService : FirebaseMessagingService() {

    @Inject lateinit var pushReceiver: PushReceiver
    @Inject lateinit var handler: PushRegistrationHandler
    @Inject lateinit var tokenFetcher: TokenFetcher

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token")
        tokenFetcher.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "Received a push notification.")
        pushReceiver.onPushDataReceived(message.data)
    }
}
