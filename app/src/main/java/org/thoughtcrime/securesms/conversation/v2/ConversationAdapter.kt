package org.thoughtcrime.securesms.conversation.v2

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewVisibleMessageBinding
import org.thoughtcrime.securesms.conversation.paging.ConversationPagerDiffCallback
import org.thoughtcrime.securesms.conversation.paging.MessageAndContact
import org.thoughtcrime.securesms.conversation.v2.messages.ControlMessageView
import org.thoughtcrime.securesms.conversation.v2.messages.VisibleMessageView
import org.thoughtcrime.securesms.conversation.v2.messages.VisibleMessageViewDelegate
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.preferences.PrivacySettingsActivity

class ConversationAdapter(
    context: Context,
    private val onItemPress: (MessageRecord, Int, VisibleMessageView, MotionEvent) -> Unit,
    private val onItemSwipeToReply: (MessageRecord, Int) -> Unit,
    private val onItemLongPress: (MessageRecord, Int, VisibleMessageView) -> Unit,
    private val onDeselect: (MessageRecord, Int) -> Unit,
    private val glide: GlideRequests,
) : PagingDataAdapter<MessageAndContact, ViewHolder>(ConversationPagerDiffCallback()) {
    private val messageDB by lazy { DatabaseComponent.get(context).mmsSmsDatabase() }
    private val contactDB by lazy { DatabaseComponent.get(context).sessionContactDatabase() }
    var selectedItems = mutableSetOf<MessageRecord>()
    private var searchQuery: String? = null
    var visibleMessageViewDelegate: VisibleMessageViewDelegate? = null

    sealed class ViewType(val rawValue: Int) {
        object Visible : ViewType(0)
        object Control : ViewType(1)

        companion object {

            val allValues: Map<Int, ViewType> get() = mapOf(
                Visible.rawValue to Visible,
                Control.rawValue to Control
            )
        }
    }

    class VisibleMessageViewHolder(val view: View) : ViewHolder(view)
    class ControlMessageViewHolder(val view: ControlMessageView) : ViewHolder(view)

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)!!.message
        if (message.isControlMessage) { return ViewType.Control.rawValue }
        return ViewType.Visible.rawValue
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val (message, contact) = getItem(position)!!
        val beforePosition = position + 1
        val afterPosition = position - 1
        val hasBefore = beforePosition in 0 until itemCount
        val hasAfter = afterPosition in 0 until itemCount
        val messageBefore = if (hasBefore) getItem(beforePosition)!! else null
        val messageAfter = if (hasAfter) getItem(afterPosition)!! else null
        when (viewHolder) {
            is VisibleMessageViewHolder -> {
                val visibleMessageView = ViewVisibleMessageBinding.bind(viewHolder.view).visibleMessageView
                val isSelected = selectedItems.contains(message)
                visibleMessageView.snIsSelected = isSelected
                visibleMessageView.indexInAdapter = position
                val senderId = message.individualRecipient.address.serialize()

                visibleMessageView.bind(message, messageBefore?.message, messageAfter?.message, glide, searchQuery, contact, senderId, visibleMessageViewDelegate)
                if (!message.isDeleted) {
                    visibleMessageView.onPress = { event -> onItemPress(message, viewHolder.adapterPosition, visibleMessageView, event) }
                    visibleMessageView.onSwipeToReply = { onItemSwipeToReply(message, viewHolder.adapterPosition) }
                    visibleMessageView.onLongPress = { onItemLongPress(message, viewHolder.adapterPosition, visibleMessageView) }
                } else {
                    visibleMessageView.onPress = null
                    visibleMessageView.onSwipeToReply = null
                    visibleMessageView.onLongPress = null
                }
            }
            is ControlMessageViewHolder -> {
                viewHolder.view.bind(message, messageBefore?.message)
                if (message.isCallLog && message.isFirstMissedCall) {
                    viewHolder.view.setOnClickListener {
                        val context = viewHolder.view.context
                        AlertDialog.Builder(context)
                            .setTitle(R.string.CallNotificationBuilder_first_call_title)
                            .setMessage(R.string.CallNotificationBuilder_first_call_message)
                            .setPositiveButton(R.string.activity_settings_title) { _, _ ->
                                val intent = Intent(context, PrivacySettingsActivity::class.java)
                                context.startActivity(intent)
                            }
                            .setNeutralButton(R.string.cancel) { d, _ ->
                                d.dismiss()
                            }
                            .show()
                    }
                } else {
                    viewHolder.view.setOnClickListener(null)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        @Suppress("NAME_SHADOWING")
        val viewType = ViewType.allValues[viewType]
        return when (viewType) {
            ViewType.Visible -> VisibleMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_visible_message, parent, false))
            ViewType.Control -> ControlMessageViewHolder(ControlMessageView(parent.context))
            else -> throw IllegalStateException("Unexpected view type: $viewType.")
        }
    }

    fun toggleSelection(message: MessageRecord, position: Int) {
        if (selectedItems.contains(message)) selectedItems.remove(message) else selectedItems.add(message)
        notifyItemChanged(position)
    }

    override fun onViewRecycled(viewHolder: ViewHolder) {
        super.onViewRecycled(viewHolder)
        when (viewHolder) {
            is VisibleMessageViewHolder -> viewHolder.view.findViewById<VisibleMessageView>(R.id.visibleMessageView).recycle()
            is ControlMessageViewHolder -> viewHolder.view.recycle()
        }
    }

//    private fun getMessageBefore(position: Int, cursor: Cursor): MessageRecord? {
//        // The message that's visually before the current one is actually after the current
//        // one for the cursor because the layout is reversed
//        if (!cursor.moveToPosition(position + 1)) { return null }
//        return messageDB.readerFor(cursor).current
//    }
//
//    private fun getMessageAfter(position: Int, cursor: Cursor): MessageRecord? {
//        // The message that's visually after the current one is actually before the current
//        // one for the cursor because the layout is reversed
//        if (!cursor.moveToPosition(position - 1)) { return null }
//        return messageDB.readerFor(cursor).current
//    }

//    override fun changeCursor(cursor: Cursor?) {
//        super.changeCursor(cursor)
//        val toRemove = mutableSetOf<MessageRecord>()
//        val toDeselect = mutableSetOf<Pair<Int, MessageRecord>>()
//        for (selected in selectedItems) {
//            val position = getItemPositionForTimestamp(selected.timestamp)
//            if (position == null || position == -1) {
//                toRemove += selected
//            } else {
//                val item = getMessage(getCursorAtPositionOrThrow(position))
//                if (item == null || item.isDeleted) {
//                    toDeselect += position to selected
//                }
//            }
//        }
//        selectedItems -= toRemove
//        toDeselect.iterator().forEach { (pos, record) ->
//            onDeselect(record, pos)
//        }
//    }

    fun findLastSeenItemPosition(lastSeenTimestamp: Long): Int? {
//        val cursor = this.cursor
//        if (lastSeenTimestamp <= 0L || cursor == null || !isActiveCursor) return null
//        for (i in 0 until itemCount) {
//            cursor.moveToPosition(i)
//            val message = messageDB.readerFor(cursor).current
//            if (message.isOutgoing || message.dateReceived <= lastSeenTimestamp) { return i }
//        }
        return null
    }

    fun getItemPositionForTimestamp(timestamp: Long): Int? {
        return null
//        val cursor = this.cursor
//        if (timestamp <= 0L || cursor == null || !isActiveCursor) return null
//        for (i in 0 until itemCount) {
//            cursor.moveToPosition(i)
//            val message = messageDB.readerFor(cursor).current
//            if (message.dateSent == timestamp) { return i }
//        }
//        return null
    }

    fun onSearchQueryUpdated(query: String?) {
        this.searchQuery = query
        notifyDataSetChanged()
    }
}