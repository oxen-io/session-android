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
        FirebaseMessaging.getInstance().token.await().takeIf { isActive } ?: throw Exception("Firebase token is null")
    }
}