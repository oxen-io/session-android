package org.thoughtcrime.securesms.preferences

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

class BlockedContactsLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs), View.OnClickListener {

    override fun onClick(v: View?) {
        if (v === this) {
            // open blocked contacts
            val intent = Intent(context, BlockedContactsActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        setOnClickListener(this)
    }
}