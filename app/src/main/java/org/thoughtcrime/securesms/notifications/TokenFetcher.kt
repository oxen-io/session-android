package org.thoughtcrime.securesms.notifications

interface TokenFetcher {
    suspend fun fetch(): String?
}
