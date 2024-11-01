package org.session.libsession.messaging.notifications

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

interface TokenFetcher {
    suspend fun fetch(): String {
        return token.filterNotNull().first()
    }

    val token: StateFlow<String?>

    fun onNewToken(token: String)
}
