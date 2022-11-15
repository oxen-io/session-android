package org.thoughtcrime.securesms.conversation.paging

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.recyclerview.widget.DiffUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord

private fun config() = PagingConfig(
    pageSize = 50,
)

fun ConversationPager(threadId: Long, db: MmsSmsDatabase) = Pager(config()) {
    ConversationPagingSource(threadId, db)
}

class ConversationPagerDiffCallback: DiffUtil.ItemCallback<MessageRecord>() {
    override fun areItemsTheSame(oldItem: MessageRecord, newItem: MessageRecord): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: MessageRecord, newItem: MessageRecord): Boolean =
        oldItem == newItem
}

class ConversationPagingSource(
    private val threadId: Long,
    private val db: MmsSmsDatabase
    ): PagingSource<Long, MessageRecord>() {
    override fun getRefreshKey(state: PagingState<Long, MessageRecord>): Long? =
        state.firstItemOrNull()?.dateSent

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, MessageRecord> {
        val fromTime = params.key ?: 0 // 0 will be treated as no offset if passed to DB
        val amount = params.loadSize
        val prevKey: Long?
        val result = withContext(Dispatchers.IO) {
            val cursor = db.getConversationPage(threadId, fromTime, amount)
            val processedList = mutableListOf<MessageRecord>()
            val reader = db.readerFor(cursor)
            while (reader.next != null) {
                reader.current?.let { item ->
                    processedList += item
                }
            }
            reader.close()
            processedList.toList()
        }
        val nextKey = if (result.isEmpty()) null else result.last().dateSent
        prevKey = if (fromTime == 0L || result.isEmpty()) null else withContext(Dispatchers.IO) {
            val cursor = db.getConversationPage(threadId, result.first().dateSent, amount)
            val lastTimestamp =
                if (!cursor.moveToLast()) null
                else {
                    val reader = db.readerFor(cursor)
                    reader.current?.dateSent
                }
            cursor.close()
            lastTimestamp
        }
        return LoadResult.Page(
            data = result,
            prevKey,
            nextKey
        )
    }
}