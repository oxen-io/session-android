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
    pageSize = 25,
    maxSize = 100,
    enablePlaceholders = false
)

fun Long.bucketed(): Long = (TIME_BUCKET - this % TIME_BUCKET) + this

fun conversationPager(threadId: Long, initialKey: PageLoad? = null, db: MmsSmsDatabase, contactDb: SessionContactDatabase) = Pager(config(), initialKey = initialKey) {
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
        val next = anchorPage.nextKey?.fromTime
        val previous = anchorPage.prevKey?.fromTime ?: anchorPage.data.firstOrNull()?.message?.dateSent ?: return null
        return PageLoad(previous, next)
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
                record?.dateSent?.let { fromTime ->
                    PageLoad(fromTime)
                }
            }
        } ?: return LoadResult.Page(emptyList(), null, null)

        val result = withContext(Dispatchers.IO) {
            val cursor = messageDb.getConversationPage(
                threadId,
                pageLoad.fromTime,
                pageLoad.toTime ?: -1L,
                params.loadSize
            )
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

        val hasNext = withContext(Dispatchers.IO) {
            if (result.isEmpty()) return@withContext false
            val lastTime = result.last().message.dateSent
            messageDb.hasNextPage(threadId, lastTime)
        }

        val nextCheckTime = if (hasNext) {
            val lastSent = result.last().message.dateSent
            if (lastSent == pageLoad.fromTime) null else lastSent
        } else null

        val hasPrevious = withContext(Dispatchers.IO) { messageDb.hasPreviousPage(threadId, pageLoad.fromTime) }
        val nextKey = if (!hasNext) null else nextCheckTime
        val prevKey = if (!hasPrevious) null else messageDb.getPreviousPage(threadId, pageLoad.fromTime, params.loadSize)

        return LoadResult.Page(
            data = result, // next check time is not null if drop is true
            prevKey = prevKey?.let { PageLoad(it, pageLoad.fromTime) },
            nextKey = nextKey?.let { PageLoad(it) }
        )
    }
}
