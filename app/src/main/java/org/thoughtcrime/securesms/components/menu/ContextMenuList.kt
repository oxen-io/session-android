package org.thoughtcrime.securesms.components.menu

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

/**
 * Handles the setup and display of actions shown in a context menu.
 */
class ContextMenuList(recyclerView: RecyclerView, onItemClick: () -> Unit) {

  private val mappingAdapter = MappingAdapter().apply {
    registerFactory(DisplayItem::class.java, LayoutFactory({ ItemViewHolder(it, onItemClick) }, R.layout.context_menu_item))
  }

  init {
    recyclerView.apply {
      adapter = mappingAdapter
      layoutManager = LinearLayoutManager(context)
      itemAnimator = null
    }
  }

  fun setItems(items: List<ActionItem>) {
    mappingAdapter.submitList(items.toAdapterItems())
  }

  private fun List<ActionItem>.toAdapterItems(): List<DisplayItem> =
    mapIndexed { index, item ->
      when {
        size == 1 -> DisplayType.ONLY
        index == 0 -> DisplayType.TOP
        index == size - 1 -> DisplayType.BOTTOM
        else -> DisplayType.MIDDLE
      }.let { DisplayItem(item, it) }
    }

  private data class DisplayItem(
    val item: ActionItem,
    val displayType: DisplayType
  ) : MappingModel<DisplayItem> {
    override fun areItemsTheSame(newItem: DisplayItem): Boolean = this == newItem

    override fun areContentsTheSame(newItem: DisplayItem): Boolean = this == newItem
  }

  private enum class DisplayType {
    TOP, BOTTOM, MIDDLE, ONLY
  }

  private class ItemViewHolder(
    itemView: View,
    private val onItemClick: () -> Unit,
  ) : MappingViewHolder<DisplayItem>(itemView) {
    private var subtitleJob: Job? = null
    val icon: ImageView = itemView.findViewById(R.id.context_menu_item_icon)
    val title: TextView = itemView.findViewById(R.id.context_menu_item_title)
    val subtitle: TextView = itemView.findViewById(R.id.context_menu_item_subtitle)

    override fun bind(model: DisplayItem) {
      val item = model.item
      val color = item.color?.let { ContextCompat.getColor(context, it) }

      if (item.iconRes > 0) {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(item.iconRes, typedValue, true)
        icon.setImageDrawable(ContextCompat.getDrawable(context, typedValue.resourceId))

        icon.imageTintList = color?.let(ColorStateList::valueOf)
      }
      item.contentDescription?.let(context.resources::getString)?.let { itemView.contentDescription = it }
      title.setText(item.title)
      color?.let(title::setTextColor)
      color?.let(subtitle::setTextColor)
      subtitle.isGone = true
      item.subtitle?.let { startSubtitleJob(subtitle, it) }
      itemView.setOnClickListener {
        item.action.run()
        onItemClick()
      }

      when (model.displayType) {
        DisplayType.TOP -> R.drawable.context_menu_item_background_top
        DisplayType.BOTTOM -> R.drawable.context_menu_item_background_bottom
        DisplayType.MIDDLE -> R.drawable.context_menu_item_background_middle
        DisplayType.ONLY -> R.drawable.context_menu_item_background_only
      }.let(itemView::setBackgroundResource)
    }

    private fun startSubtitleJob(textView: TextView, getSubtitle: (Context) -> CharSequence?) {
      fun updateText() = getSubtitle(context).let {
        textView.isGone = it == null
        textView.text = it
      }
      updateText()

      subtitleJob?.cancel()
      subtitleJob = CoroutineScope(Dispatchers.Main).launch {
        while (true) {
          updateText()
          delay(200)
        }
      }
    }

    override fun onDetachedFromWindow() {
      super.onDetachedFromWindow()
      // naive job cancellation, will break if many items are added to context menu.
      subtitleJob?.cancel()
    }
  }
}
