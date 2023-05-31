package org.thoughtcrime.securesms.preferences

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import network.loki.messenger.R
import network.loki.messenger.databinding.BlockedContactLayoutBinding
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.util.adapter.SelectableItem

typealias SelectableRecipient = SelectableItem<Recipient>

class BlockedContactsAdapter(val viewModel: BlockedContactsViewModel) : ListAdapter<SelectableRecipient,BlockedContactsAdapter.ViewHolder>(RecipientDiffer()) {

    class RecipientDiffer: DiffUtil.ItemCallback<SelectableRecipient>() {
        override fun areItemsTheSame(old: SelectableRecipient, new: SelectableRecipient) = old.item.address == new.item.address
        override fun areContentsTheSame(old: SelectableRecipient, new: SelectableRecipient) = old.isSelected == new.isSelected
        override fun getChangePayload(old: SelectableRecipient, new: SelectableRecipient) = new.isSelected
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        LayoutInflater.from(parent.context)
            .inflate(R.layout.blocked_contact_layout, parent, false)
            .let(::ViewHolder)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), viewModel::toggle)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) holder.bind(getItem(position), viewModel::toggle)
        else holder.select(getItem(position).isSelected)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.binding.profilePictureView.root.recycle()
    }

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {

        val glide = GlideApp.with(itemView)
        val binding = BlockedContactLayoutBinding.bind(itemView)

        fun bind(selectable: SelectableRecipient, toggle: (SelectableRecipient) -> Unit) {
            binding.recipientName.text = selectable.item.name
            with (binding.profilePictureView.root) {
                glide = this@ViewHolder.glide
                update(selectable.item)
            }
            binding.root.setOnClickListener { toggle(selectable) }
            binding.selectButton.isSelected = selectable.isSelected
        }

        fun select(isSelected: Boolean) {
            binding.selectButton.isSelected = isSelected
        }
    }
}
