package org.thoughtcrime.securesms.home

import android.content.Context
import androidx.recyclerview.widget.DiffUtil
import org.thoughtcrime.securesms.database.model.ThreadRecord

class HomeDiffUtil(
    private val old: List<ThreadRecord>,
    private val new: List<ThreadRecord>,
    private val context: Context
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

        // Note: For some reason the 'hashCode' value can change after initialisation so we can't cache it
        if (isSameItem) { isSameItem = (oldItem.recipient.hashCode() == newItem.recipient.hashCode()) }

        // Note: Two instances of 'SpannableString' may not equate even though their content matches
        if (isSameItem) { isSameItem = (oldItem.getDisplayBody(context).toString() == newItem.getDisplayBody(context).toString()) }

        if (isSameItem) {
            isSameItem = (
                oldItem.isFailed == newItem.isFailed &&
                oldItem.isDelivered == newItem.isDelivered &&
                oldItem.isSent == newItem.isSent &&
                oldItem.isPending == newItem.isPending
            )
        }

        return isSameItem
    }

}