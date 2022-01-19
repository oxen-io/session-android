package org.thoughtcrime.securesms.home.search

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import network.loki.messenger.databinding.ViewGlobalSearchInputBinding

class GlobalSearchInputLayout @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    lateinit var binding: ViewGlobalSearchInputBinding

    init {
        binding = ViewGlobalSearchInputBinding.inflate(LayoutInflater.from(context))
        addView(binding.root)
    }

}