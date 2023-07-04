package org.thoughtcrime.securesms.preferences

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import network.loki.messenger.databinding.BlockedContactsPreferenceBinding

class BlockedContactsPreference @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : PreferenceCategory(context, attributeSet) {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val binding = BlockedContactsPreferenceBinding.bind(holder.itemView)
        binding.blockedContactButton.setOnClickListener {
            val intent = Intent(context, BlockedContactsActivity::class.java)
            context.startActivity(intent)
        }
    }
}
