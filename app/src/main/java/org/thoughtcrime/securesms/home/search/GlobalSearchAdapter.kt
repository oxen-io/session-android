package org.thoughtcrime.securesms.home.search

import android.content.Context
import android.database.Cursor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewGlobalSearchHeaderBinding
import network.loki.messenger.databinding.ViewGlobalSearchResultBinding
import org.session.libsession.messaging.contacts.Contact as ContactModel
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.search.model.MessageResult
import java.security.InvalidParameterException

class GlobalSearchAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val HEADER_VIEW_TYPE = 0
        const val CONTENT_VIEW_TYPE = 1
    }

    private var data: List<Model> = listOf()
    private var query: String? = null

    fun setNewData(query: String, newData: List<Model>) {
        this.query = query
        val diffResult = DiffUtil.calculateDiff(GlobalSearchDiff(data, newData))
        data = newData
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemViewType(position: Int): Int =
        if (data[position] is Model.Header) HEADER_VIEW_TYPE else CONTENT_VIEW_TYPE

    override fun getItemCount(): Int = data.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == HEADER_VIEW_TYPE) {
            HeaderView(LayoutInflater.from(parent.context).inflate(R.layout.view_global_search_header, parent, false))
        } else {
            ContentView(LayoutInflater.from(parent.context).inflate(R.layout.view_global_search_result, parent, false))
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderView) {
            holder.bind(data[position] as Model.Header)
        } else if (holder is ContentView) {
            holder.bind(data[position])
        }
    }

    class HeaderView(view: View): RecyclerView.ViewHolder(view) {

        val binding = ViewGlobalSearchHeaderBinding.bind(view)

        fun bind(header: Model.Header) {
            binding.searchHeader.setText(header.title)
        }
    }

    class ContentView(view: View): RecyclerView.ViewHolder(view) {
        fun bind(model: Model) {
            if (model is Model.Header) throw InvalidParameterException("Can't display Model.Header as ContentView")
        }
    }

    sealed class Model {
        data class Header(@StringRes val title: Int): Model()
        data class Contact(val contact: ContactModel): Model()
        data class Conversation(val conversation: ThreadRecord): Model()
        data class Message(val messageResult: MessageResult): Model()
    }

    class GlobalSearchDiff(private val oldData: List<Model>, private val newData: List<Model>): DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldData.size
        override fun getNewListSize(): Int = newData.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = oldData[oldItemPosition] == newData[newItemPosition]
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = oldData[oldItemPosition] == newData[newItemPosition]
    }

}