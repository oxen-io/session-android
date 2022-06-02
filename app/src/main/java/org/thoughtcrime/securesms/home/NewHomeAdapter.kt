package org.thoughtcrime.securesms.home

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.mms.GlideRequests

class NewHomeAdapter(private val context: Context, private val listener: ConversationClickListener):
    RecyclerView.Adapter<NewHomeAdapter.ViewHolder>() {

    private var _data: List<ThreadRecord> = emptyList()
    var data: List<ThreadRecord>
        get() = _data.toList()
        set(newData) {
            val previousData = _data.toList()
            val diff = HomeDiffUtil(previousData, newData, context)
            val diffResult = DiffUtil.calculateDiff(diff)
            _data = newData
            diffResult.dispatchUpdatesTo(this)
        }

    override fun getItemId(position: Int): Long = _data[position].threadId

    lateinit var glide: GlideRequests
    var typingThreadIDs = setOf<Long>()
        set(value) {
            field = value
            // TODO: replace this with a diffed update or a partial change set with payloads
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = ConversationView(context)
        view.setOnClickListener { view.thread?.let { listener.onConversationClick(it) } }
        view.setOnLongClickListener {
            view.thread?.let { listener.onLongConversationClick(it) }
            true
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val thread = data[position]
        val isTyping = typingThreadIDs.contains(thread.threadId)
        holder.view.bind(thread, isTyping, glide)
    }

    override fun getItemCount(): Int = data.size

    class ViewHolder(val view: ConversationView) : RecyclerView.ViewHolder(view)

}