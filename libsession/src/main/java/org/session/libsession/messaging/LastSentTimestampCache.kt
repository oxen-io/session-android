package org.session.libsession.messaging

interface LastSentTimestampCache {
    fun getTimestamp(threadId: Long): Long?
    fun submitTimestamp(threadId: Long, timestamp: Long)
    fun delete(threadId: Long, timestamps: List<Long>)
    fun delete(threadId: Long, timestamp: Long) = delete(threadId, listOf(timestamp))
    fun refresh(threadId: Long)
}
