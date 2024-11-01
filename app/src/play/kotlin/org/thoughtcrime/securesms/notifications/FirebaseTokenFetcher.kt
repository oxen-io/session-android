package org.thoughtcrime.securesms.notifications

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.tasks.await
import org.session.libsession.messaging.notifications.TokenFetcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseTokenFetcher @Inject constructor(): TokenFetcher {
    override val token = MutableStateFlow<String?>(null)

    init {
        FirebaseMessaging.getInstance()
            .token
            .addOnSuccessListener(this::onNewToken)
    }

    override fun onNewToken(token: String) {
        this.token.value = token
    }
}