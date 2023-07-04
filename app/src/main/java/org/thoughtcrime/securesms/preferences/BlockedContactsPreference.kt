package org.thoughtcrime.securesms.preferences

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder

class BlockedContactsPreference @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : PreferenceCategory(context, attributeSet) {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.itemView.setOnClickListener {
            Intent(context, BlockedContactsActivity::class.java).let(context::startActivity)
        }
    }
}
