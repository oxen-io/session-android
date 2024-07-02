package org.thoughtcrime.securesms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import network.loki.messenger.R
import network.loki.messenger.databinding.MediaOverviewGalleryItemBinding
import org.thoughtcrime.securesms.database.MediaDatabase.MediaRecord
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.util.MediaUtil

class MediaGalleryAdapter(private val itemClickListener: ItemClickListener): RecyclerView.Adapter<MediaGalleryAdapter.ViewHolder>() {

    private val items: MutableList<MediaRecord> = mutableListOf()
    private val selectedItems: MutableSet<MediaRecord> = mutableSetOf()

    fun setItems(newItems: List<MediaRecord>) {
        items.clear()
        items += newItems
        notifyDataSetChanged()
    }

    fun toggleSelection(record: MediaRecord) {
        val index = items.indexOf(record)
        if (index >= 0) {
            if (selectedItems.contains(record)) selectedItems -= record
            else selectedItems += record
            notifyItemChanged(index)
        }
    }

    fun getSelectedMediaCount() = selectedItems.size

    fun selectAllMedia() {
        selectedItems += items
        val size = items.size
        notifyItemRangeChanged(0, size)
    }

    fun getSelectedMedia() = selectedItems.toList()

    fun clearSelection() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.media_overview_gallery_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, selectedItems.contains(item), itemClickListener)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {

        private val binding = MediaOverviewGalleryItemBinding.bind(itemView)
        private val glide = GlideApp.with(itemView)

        fun bind(item: MediaRecord, isSelected: Boolean, itemClickListener: ItemClickListener) {
            val slide = MediaUtil.getSlideForAttachment(itemView.context, item.attachment)

            if (slide != null) {
                binding.image.root.setImageResource(glide, slide, false)
            }

            binding.image.root.setOnClickListener { itemClickListener.onMediaClicked(item) }
            binding.image.root.setOnLongClickListener {
                itemClickListener.onMediaLongClicked(item)
                true
            }
            binding.selectedIndicator.isVisible = isSelected
        }
    }

    interface ItemClickListener {
        fun onMediaClicked(mediaRecord: MediaRecord)
        fun onMediaLongClicked(mediaRecord: MediaRecord?)
    }

}