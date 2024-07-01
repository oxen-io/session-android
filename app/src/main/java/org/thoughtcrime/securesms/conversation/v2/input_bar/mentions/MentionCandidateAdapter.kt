package org.thoughtcrime.securesms.conversation.v2.input_bar.mentions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import network.loki.messenger.databinding.ViewMentionCandidateV2Binding
import org.thoughtcrime.securesms.conversation.v2.mention.MentionViewModel

class MentionCandidateAdapter(
    private val onCandidateSelected: ((MentionViewModel.Candidate) -> Unit)
) : RecyclerView.Adapter<MentionCandidateAdapter.ViewHolder>() {
    var candidates = listOf<MentionViewModel.Candidate>()
        set(newValue) {
            if (field != newValue) {
                val result = DiffUtil.calculateDiff(object  : DiffUtil.Callback() {
                    override fun getOldListSize() = field.size
                    override fun getNewListSize() = newValue.size
                    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int)
                        = field[oldItemPosition].member.publicKey == newValue[newItemPosition].member.publicKey
                    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int)
                        = field[oldItemPosition] == newValue[newItemPosition]
                })

                field = newValue
                result.dispatchUpdatesTo(this)
            }
        }

    class ViewHolder(val binding: ViewMentionCandidateV2Binding)
        : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ViewMentionCandidateV2Binding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = candidates.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val candidate = candidates[position]
        holder.binding.update(candidate)
        holder.binding.root.setOnClickListener { onCandidateSelected(candidate) }
    }
}