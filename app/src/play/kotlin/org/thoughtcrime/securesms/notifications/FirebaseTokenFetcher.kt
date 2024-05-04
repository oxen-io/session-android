package org.thoughtcrime.securesms.notifications

import com.google.firebase.iid.FirebaseInstanceId

/*
// For firebase v24.0.0 and above we would have to use the following instead:
import com.google.firebase.messaging.FirebaseMessaging
*/

import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseTokenFetcher @Inject constructor(): TokenFetcher {
    override suspend fun fetch() = withContext(Dispatchers.IO) {
        // Potential new firebase code for new Firebase versions circa 24.0.0
        // TODO: When we actually update the `firebaseMessagingVersion` in gradle then look into this
        // TODO: because I believe it's causing a token decrypt failure. We get issues like:
        /*
        E  Invalid push notification
        java.lang.IllegalStateException: no metadata
          at org.thoughtcrime.securesms.notifications.PushReceiver.decrypt(PushReceiver.kt:93)
          at org.thoughtcrime.securesms.notifications.PushReceiver.asByteArray(PushReceiver.kt:70)
          at org.thoughtcrime.securesms.notifications.PushReceiver.onPush(PushReceiver.kt:35)
          at org.thoughtcrime.securesms.notifications.FirebasePushService.onMessageReceived(FirebasePushService.kt:26)
          at com.google.firebase.messaging.FirebaseMessagingService.dispatchMessage(FirebaseMessagingService.java:243)
          at com.google.firebase.messaging.FirebaseMessagingService.passMessageIntentToSdk(FirebaseMessagingService.java:193)
          at com.google.firebase.messaging.FirebaseMessagingService.handleMessageIntent(FirebaseMessagingService.java:179)
          at com.google.firebase.messaging.FirebaseMessagingService.handleIntent(FirebaseMessagingService.java:168)
          at com.google.firebase.messaging.EnhancedIntentService.lambda$processIntent$0$com-google-firebase-messaging-EnhancedIntentService(EnhancedIntentService.java:82)
          at com.google.firebase.messaging.EnhancedIntentService$$ExternalSyntheticLambda1.run(D8$$SyntheticClass:0)
          at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1167)
          at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:641)
          at com.google.android.gms.common.util.concurrent.zza.run(com.google.android.gms:play-services-basement@@18.3.0:2)
          at java.lang.Thread.run(Thread.java:764)
         */
        /*FirebaseMessaging.getInstance().token
            .also(Tasks::await)
            .takeIf { isActive } // don't 'complete' task if we were canceled
            ?.run { result ?: throw exception!! } // This doesn't have a `.token` property - perhaps we can use just `toString()`?
        */

        // Original firebase code that works with firebase messenger v18.0.0
        // TODO: Even using the v18.0.0 that we've used for ages I'm still seeing issues when making calls (it works - but we get errors in console):
        // TODO: Check if this issue exists in current master and dev.
        /*
         E  Invalid push notification (Ask Gemini)
            java.lang.IllegalStateException: no metadata
            at org.thoughtcrime.securesms.notifications.PushReceiver.decrypt(PushReceiver.kt:93)
            at org.thoughtcrime.securesms.notifications.PushReceiver.asByteArray(PushReceiver.kt:70)
            at org.thoughtcrime.securesms.notifications.PushReceiver.onPush(PushReceiver.kt:35)
            at org.thoughtcrime.securesms.notifications.FirebasePushService.onMessageReceived(FirebasePushService.kt:26)
            at com.google.firebase.messaging.FirebaseMessagingService.zzd(Unknown Source:67)
            at com.google.firebase.iid.zzb.run(Unknown Source:2)
            at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1167)
            at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:641)
            at com.google.android.gms.common.util.concurrent.zza.run(Unknown Source:6)
            at java.lang.Thread.run(Thread.java:764)
        */
        FirebaseInstanceId.getInstance().instanceId
            .also(Tasks::await)
            .takeIf { isActive } // don't 'complete' task if we were canceled
            ?.run { result?.token ?: throw exception!! }
    }
}
