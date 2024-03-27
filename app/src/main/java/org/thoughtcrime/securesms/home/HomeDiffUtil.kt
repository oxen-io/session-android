package org.thoughtcrime.securesms.home

import android.content.Context
import androidx.recyclerview.widget.DiffUtil
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.util.getConversationUnread

class HomeDiffUtil(
    private val old: List<ThreadRecord>,
    private val new: List<ThreadRecord>,
    private val context: Context,
    private val configFactory: ConfigFactory
): DiffUtil.Callback() {

    override fun getOldListSize(): Int = old.size

    override fun getNewListSize(): Int = new.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        old[oldItemPosition].threadId == new[newItemPosition].threadId

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = old[oldItemPosition]
        val newItem = new[newItemPosition]

        // return early to save getDisplayBody or expensive calls
        var isSameItem = true

        if (isSameItem) { isSameItem = (oldItem.count == newItem.count) }
        if (isSameItem) { isSameItem = (oldItem.unreadCount == newItem.unreadCount) }
        if (isSameItem) { isSameItem = (oldItem.isPinned == newItem.isPinned) }

        // The recipient is passed as a reference and changes to recipients update the reference so we
        // need to cache the hashCode for the recipient and use that for diffing - unfortunately
        // recipient data is also loaded asyncronously which means every thread will refresh at least
        // once when the initial recipient data is loaded
        if (isSameItem) { isSameItem = (oldItem.initialRecipientHash == newItem.initialRecipientHash) }

        // Note: Two instances of 'SpannableString' may not equate even though their content matches
        if (isSameItem) { isSameItem = (oldItem.getDisplayBody(context).toString() == newItem.getDisplayBody(context).toString()) }

        if (isSameItem) {
            isSameItem = (
                oldItem.isFailed == newItem.isFailed &&
                oldItem.isDelivered == newItem.isDelivered &&
                oldItem.isSent == newItem.isSent &&
                oldItem.isPending == newItem.isPending &&
                oldItem.lastSeen == newItem.lastSeen &&
                configFactory.convoVolatile?.getConversationUnread(newItem) != true
            )
        }

        return isSameItem
    }

}