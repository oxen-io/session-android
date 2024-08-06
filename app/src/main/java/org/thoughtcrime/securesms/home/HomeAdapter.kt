package org.thoughtcrime.securesms.home

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_ID
import com.bumptech.glide.RequestManager
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewMessageRequestBannerBinding
import org.thoughtcrime.securesms.dependencies.ConfigFactory

class HomeAdapter(
    private val context: Context,
    private val configFactory: ConfigFactory,
    private val listener: ConversationClickListener,
    private val showMessageRequests: () -> Unit,
    private val hideMessageRequests: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), ListUpdateCallback {

    companion object {
        private const val HEADER = 0
        private const val ITEM = 1
    }

    var messageRequests: HomeViewModel.MessageRequests? = null
        set(value) {
            if (field == value) return
            val hadHeader = hasHeaderView()
            field = value
            if (value != null) {
                if (hadHeader) notifyItemChanged(0) else notifyItemInserted(0)
            } else if (hadHeader) notifyItemRemoved(0)
        }

    var data: HomeViewModel.Data = HomeViewModel.Data()
        set(newData) {
            if (field === newData) return

            messageRequests = newData.messageRequests

            val diff = HomeDiffUtil(field, newData, context, configFactory)
            val diffResult = DiffUtil.calculateDiff(diff)
            field = newData
            diffResult.dispatchUpdatesTo(this as ListUpdateCallback)
        }

    fun hasHeaderView(): Boolean = messageRequests != null

    private val headerCount: Int
        get() = if (messageRequests == null) 0 else 1

    override fun onInserted(position: Int, count: Int) {
        notifyItemRangeInserted(position + headerCount, count)
    }

    override fun onRemoved(position: Int, count: Int) {
        notifyItemRangeRemoved(position + headerCount, count)
    }

    override fun onMoved(fromPosition: Int, toPosition: Int) {
        notifyItemMoved(fromPosition + headerCount, toPosition + headerCount)
    }

    override fun onChanged(position: Int, count: Int, payload: Any?) {
        notifyItemRangeChanged(position + headerCount, count, payload)
    }

    override fun getItemId(position: Int): Long  {
        if (hasHeaderView() && position == 0) return NO_ID
        val offsetPosition = if (hasHeaderView()) position-1 else position
        return data.threads[offsetPosition].threadId
    }

    lateinit var glide: RequestManager

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            HEADER -> {
                ViewMessageRequestBannerBinding.inflate(LayoutInflater.from(parent.context)).apply {
                    root.setOnClickListener { showMessageRequests() }
                    root.setOnLongClickListener { hideMessageRequests(); true }
                    root.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
                }.let(::HeaderFooterViewHolder)
            }
            ITEM -> {
                val conversationView = LayoutInflater.from(parent.context).inflate(R.layout.view_conversation, parent, false) as ConversationView
                val viewHolder = ConversationViewHolder(conversationView)
                viewHolder.view.setOnClickListener { viewHolder.view.thread?.let { listener.onConversationClick(it) } }
                viewHolder.view.setOnLongClickListener {
                    viewHolder.view.thread?.let { listener.onLongConversationClick(it) }
                    true
                }
                viewHolder
            }
            else -> throw Exception("viewType $viewType isn't valid")
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderFooterViewHolder -> {
                holder.binding.run {
                    messageRequests?.let {
                        unreadCountTextView.text = it.count
                    }
                }
            }
            is ConversationViewHolder -> {
                val offset = if (hasHeaderView()) position - 1 else position
                val thread = data.threads[offset]
                val isTyping = data.typingThreadIDs.contains(thread.threadId)
                holder.view.bind(thread, isTyping)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is ConversationViewHolder) {
            holder.view.recycle()
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (hasHeaderView() && position == 0) HEADER
        else ITEM

    override fun getItemCount(): Int = data.threads.size + if (hasHeaderView()) 1 else 0

    class ConversationViewHolder(val view: ConversationView) : RecyclerView.ViewHolder(view)

    class HeaderFooterViewHolder(val binding: ViewMessageRequestBannerBinding) : RecyclerView.ViewHolder(binding.root)
}
