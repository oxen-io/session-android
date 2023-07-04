package org.thoughtcrime.securesms.preferences

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder

class BlockedContactsPreference @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : PreferenceCategory(context, attributeSet) {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.itemView.setOnClickListener {
            val intent = Intent(context, BlockedContactsActivity::class.java)
            context.startActivity(intent)
        }
    }
}
