package org.thoughtcrime.securesms.database

import org.session.libsession.messaging.LastSentTimestampCache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastSentTimestampCache @Inject constructor(
    val mmsSmsDatabase: MmsSmsDatabase
): LastSentTimestampCache {

    private val map = mutableMapOf<Long, Long>()

    @Synchronized
    override fun getTimestamp(threadId: Long): Long? = map[threadId]

    @Synchronized
    override fun submitTimestamp(threadId: Long, timestamp: Long) {
        if (map[threadId]?.let { timestamp <= it } == true) return

        map[threadId] = timestamp
    }

    @Synchronized
    override fun delete(threadId: Long, timestamps: List<Long>) {
        if (map[threadId]?.let { it !in timestamps } == true) return
        map.remove(threadId)
        refresh(threadId)
    }

    @Synchronized
    override fun refresh(threadId: Long) {
        if (map[threadId]?.let { it > 0 } == true) return
        val lastOutgoingTimestamp = mmsSmsDatabase.getLastOutgoingTimestamp(threadId)
        if (lastOutgoingTimestamp <= 0) return
        map[threadId] = lastOutgoingTimestamp
    }
}
