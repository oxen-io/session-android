package org.thoughtcrime.securesms.home

import android.content.Context
import androidx.recyclerview.widget.DiffUtil
import org.thoughtcrime.securesms.database.model.ThreadRecord

class HomeDiffUtil(private val old: List<ThreadRecord>,
                   private val new: List<ThreadRecord>,
                   private val context: Context): DiffUtil.Callback() {
    override fun getOldListSize(): Int = old.size

    override fun getNewListSize(): Int = new.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        old[oldItemPosition].threadId == new[newItemPosition].threadId

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val sameCount = old[oldItemPosition].count == new[newItemPosition].count
        val sameSnippet = old[oldItemPosition].getDisplayBody(context) == new[newItemPosition].getDisplayBody(context)
        val sameUnreads = old[oldItemPosition].unreadCount == new[newItemPosition].unreadCount
        return sameCount && sameSnippet && sameUnreads
    }

}