package org.thoughtcrime.securesms.notifications

import com.google.firebase.messaging.FirebaseMessaging

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseTokenFetcher @Inject constructor(): TokenFetcher {
    val TAG = "FirebaseTF"

    override suspend fun fetch() = withContext(Dispatchers.IO) {
        /*
        // OG: Firebase 18.0.0 code:
        FirebaseInstanceId.getInstance().instanceId
            .also(Tasks::await)
            .takeIf { isActive } // don't 'complete' task if we were canceled
            ?.run { result?.token ?: throw exception!! }
        */

        // ACL Firebase 24.0.0 code:
        FirebaseMessaging.getInstance().token.await().takeIf { isActive } ?: throw Exception("Firebase token is null")
    }
}
