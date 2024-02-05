package org.thoughtcrime.securesms.messagerequests

import android.content.Context
import android.content.res.ColorStateList
import android.database.Cursor
import android.os.Build
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.mms.GlideRequests

class MessageRequestsAdapter(
    context: Context,
    cursor: Cursor?,
    val listener: ConversationClickListener
) : CursorRecyclerViewAdapter<MessageRequestsAdapter.ViewHolder>(context, cursor) {
    private val threadDatabase = DatabaseComponent.get(context).threadDatabase()
    lateinit var glide: GlideRequests

    class ViewHolder(val view: MessageRequestView) : RecyclerView.ViewHolder(view)

    override fun onCreateItemViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = MessageRequestView(context)
        view.setOnClickListener { view.thread?.let { listener.onConversationClick(it) } }
        view.setOnLongClickListener {
            view.thread?.let { showPopupMenu(view) }
            true
        }
        return ViewHolder(view)
    }

    override fun onBindItemViewHolder(viewHolder: ViewHolder, cursor: Cursor) {
        val thread = getThread(cursor)!!
        viewHolder.view.bind(thread, glide)
    }

    override fun onItemViewRecycled(holder: ViewHolder?) {
        super.onItemViewRecycled(holder)
        holder?.view?.recycle()
    }

    private fun showPopupMenu(view: MessageRequestView) {
        val popupMenu = PopupMenu(ContextThemeWrapper(context, R.style.PopupMenu_MessageRequests), view)
        popupMenu.menuInflater.inflate(R.menu.menu_message_request, popupMenu.menu)
        popupMenu.menu.findItem(R.id.menu_block_message_request)?.isVisible = !view.thread!!.recipient.isOpenGroupInboxRecipient
        popupMenu.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.menu_delete_message_request) {
                listener.onDeleteConversationClick(view.thread!!)
            } else if (menuItem.itemId == R.id.menu_block_message_request) {
                listener.onBlockConversationClick(view.thread!!)
            }
            true
        }
        for (i in 0 until popupMenu.menu.size()) {
            val item = popupMenu.menu.getItem(i)
            val s = SpannableString(item.title)
            s.setSpan(ForegroundColorSpan(context.getColor(R.color.destructive)), 0, s.length, 0)

            // TODO: `iconTintList` requires API 26 but our minimum is 23 - fix it!
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                item.iconTintList = ColorStateList.valueOf(context.getColor(R.color.destructive))
            }

            item.title = s
        }

        // TODO: `setForceShowIcon` require API 29 but our minimum is 23 - fix it!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            popupMenu.setForceShowIcon(true)  // Crashed here after long-press on blocked contact on API 28
            popupMenu.show()
        }

    }

    private fun getThread(cursor: Cursor): ThreadRecord? {
        return threadDatabase.readerFor(cursor).current
    }
}

interface ConversationClickListener {
    fun onConversationClick(thread: ThreadRecord)
    fun onBlockConversationClick(thread: ThreadRecord)
    fun onDeleteConversationClick(thread: ThreadRecord)
}
