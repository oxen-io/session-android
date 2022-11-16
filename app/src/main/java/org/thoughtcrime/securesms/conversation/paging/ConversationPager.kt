package org.thoughtcrime.securesms.conversation.paging

import androidx.annotation.WorkerThread
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.recyclerview.widget.DiffUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.session.libsession.messaging.contacts.Contact
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.SessionContactDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord

private const val TIME_BUCKET = 600000L // bucket into 10 minute increments

private fun config() = PagingConfig(
    pageSize = 100,
    maxSize = 500,
)

fun conversationPager(threadId: Long, db: MmsSmsDatabase, contactDb: SessionContactDatabase) = Pager(config()) {
    ConversationPagingSource(threadId, db, contactDb)
}

class ConversationPagerDiffCallback: DiffUtil.ItemCallback<MessageAndContact>() {
    override fun areItemsTheSame(oldItem: MessageAndContact, newItem: MessageAndContact): Boolean =
        oldItem.message.id == newItem.message.id && oldItem.message.isMms == newItem.message.isMms

    override fun areContentsTheSame(oldItem: MessageAndContact, newItem: MessageAndContact): Boolean =
        oldItem == newItem
}

data class MessageAndContact(val message: MessageRecord,
                          val contact: Contact?)

data class PageLoad(val fromTime: Long, val toTime: Long? = null)

class ConversationPagingSource(
    private val threadId: Long,
    private val messageDb: MmsSmsDatabase,
    private val contactDb: SessionContactDatabase
    ): PagingSource<PageLoad, MessageAndContact>() {

    override fun getRefreshKey(state: PagingState<PageLoad, MessageAndContact>): PageLoad? {
        val anchorPosition = state.anchorPosition ?: return null
        val anchorPage = state.closestPageToPosition(anchorPosition) ?: return null
        val next = anchorPage.nextKey?.fromTime ?: return null
        val previous = anchorPage.prevKey?.toTime
        return PageLoad(next, previous)
    }

    private val contactCache = mutableMapOf<String, Contact>()

    @WorkerThread
    private fun getContact(sessionId: String): Contact? {
        contactCache[sessionId]?.let { contact ->
            return contact
        } ?: run {
            contactDb.getContactWithSessionID(sessionId)?.let { contact ->
                contactCache[sessionId] = contact
                return contact
            }
        }
        return null
    }

    override suspend fun load(params: LoadParams<PageLoad>): LoadResult<PageLoad, MessageAndContact> {
        val pageLoad = params.key ?: withContext(Dispatchers.IO) {
            messageDb.getConversationSnippet(threadId).use {
                val reader = messageDb.readerFor(it)
                var record: MessageRecord? = null
                if (reader != null) {
                    record = reader.next
                    while (record != null && record.isDeleted) {
                        record = reader.next
                    }
                }
                record?.let { message ->
                    val toRound = message.dateSent
                    (TIME_BUCKET - toRound % TIME_BUCKET) + toRound
                }?.let { fromTime ->
                    PageLoad(fromTime)
                }
            }
        } ?: return LoadResult.Page(emptyList(), null, null)

        val result = withContext(Dispatchers.IO) {
            val cursor = messageDb.getConversationPage(threadId, pageLoad.fromTime, pageLoad.toTime ?: -1L, params.loadSize)
            val processedList = mutableListOf<MessageAndContact>()
            val reader = messageDb.readerFor(cursor)
            while (reader.next != null && !invalid) {
                reader.current?.let { item ->
                    val contact = getContact(item.individualRecipient.address.serialize())
                    processedList += MessageAndContact(item, contact)
                }
            }
            reader.close()
            processedList.toMutableList()
        }

        val (nextCheckTime, drop) = if (pageLoad.toTime == null) {
            // cut out the last X to bucket time, set next check time to be that time
            val toRound = result.last().message.dateSent
            (TIME_BUCKET - toRound % TIME_BUCKET) + toRound to true
        } else pageLoad.toTime to false

        val hasNext = withContext(Dispatchers.IO) { messageDb.hasNextPage(threadId, nextCheckTime) }
        val hasPrevious = withContext(Dispatchers.IO) { messageDb.hasPreviousPage(threadId, pageLoad.fromTime) }
        val nextKey = if (!hasNext) null else nextCheckTime
        val prevKey = if (!hasPrevious) null else pageLoad.fromTime + TIME_BUCKET
        return LoadResult.Page(
            data = result.dropLastWhile { drop && it.message.dateSent <= nextCheckTime },
            prevKey = prevKey?.let { PageLoad(it) },
            nextKey = nextKey?.let { PageLoad(it) }
        )
    }
}
