package org.thoughtcrime.securesms.preferences

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import network.loki.messenger.R
import network.loki.messenger.databinding.ItemSelectableBinding
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.ui.GetString
import java.util.Objects

class RadioOptionAdapter<T>(
    private var selectedOptionPosition: Int = 0,
    private val onClickListener: (RadioOption<T>) -> Unit
) : ListAdapter<RadioOption<T>, RadioOptionAdapter.ViewHolder<T>>(RadioOptionDiffer()) {

    class RadioOptionDiffer<T>: DiffUtil.ItemCallback<RadioOption<T>>() {
        override fun areItemsTheSame(oldItem: RadioOption<T>, newItem: RadioOption<T>) = oldItem.title == newItem.title
        override fun areContentsTheSame(oldItem: RadioOption<T>, newItem: RadioOption<T>) = Objects.equals(oldItem.value,newItem.value)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<T> =
        LayoutInflater.from(parent.context).inflate(R.layout.item_selectable, parent, false)
            .let(::ViewHolder)

    override fun onBindViewHolder(holder: ViewHolder<T>, position: Int) {
        holder.bind(
            option = getItem(position),
            isSelected = position == selectedOptionPosition
        ) {
            onClickListener(it)
            setSelectedPosition(position)
        }
    }

    fun setSelectedPosition(selectedPosition: Int) {
        notifyItemChanged(selectedOptionPosition)
        selectedOptionPosition = selectedPosition
        notifyItemChanged(selectedOptionPosition)
    }

    class ViewHolder<T>(itemView: View): RecyclerView.ViewHolder(itemView) {
        val glide = GlideApp.with(itemView)
        val binding = ItemSelectableBinding.bind(itemView)

        fun bind(option: RadioOption<T>, isSelected: Boolean, toggleSelection: (RadioOption<T>) -> Unit) {
            val alpha = if (option.enabled) 1f else 0.5f
            binding.root.isEnabled = option.enabled
            binding.root.contentDescription = option.contentDescription?.string(itemView.context)
            binding.titleTextView.alpha = alpha
            binding.subtitleTextView.alpha = alpha
            binding.selectButton.alpha = alpha

            binding.titleTextView.text = option.title.string(itemView.context)
            binding.subtitleTextView.text = option.subtitle?.string(itemView.context).also {
                binding.subtitleTextView.isVisible = !it.isNullOrBlank()
            }

            binding.selectButton.isSelected = isSelected
            if (option.enabled) {
                binding.root.setOnClickListener { toggleSelection(option) }
            }
        }
    }

}

data class RadioOption<out T>(
    val value: T,
    val title: GetString,
    val subtitle: GetString? = null,
    val enabled: Boolean = true,
    val contentDescription: GetString? = null
)

fun <T> radioOption(value: T, @StringRes title: Int, configure: RadioOptionBuilder<T>.() -> Unit = {}) =
    radioOption(value, GetString(title), configure)

fun <T> radioOption(value: T, title: String, configure: RadioOptionBuilder<T>.() -> Unit = {}) =
    radioOption(value, GetString(title), configure)

fun <T> radioOption(value: T, title: GetString, configure: RadioOptionBuilder<T>.() -> Unit = {}) =
    RadioOptionBuilder(value, title).also { it.configure() }.build()

class RadioOptionBuilder<out T>(
    val value: T,
    val title: GetString
) {
    var subtitle: GetString? = null
    var enabled: Boolean = true
    var contentDescription: GetString? = null

    fun subtitle(string: String) {
        subtitle = GetString(string)
    }

    fun subtitle(@StringRes stringRes: Int) {
        subtitle = GetString(stringRes)
    }

    fun contentDescription(string: String) {
        contentDescription = GetString(string)
    }

    fun contentDescription(@StringRes stringRes: Int) {
        contentDescription = GetString(stringRes)
    }

    fun build() = RadioOption(
        value,
        title,
        subtitle,
        enabled,
        contentDescription
    )
}
