package org.thoughtcrime.securesms.preferences.widgets

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import network.loki.messenger.R

class DropDownPreference : ListPreference {
    private var dropDownLabel: TextView? = null
    private var clickListener: OnPreferenceClickListener? = null
    private var onViewReady: (()->Unit)? = null

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(
        context!!, attrs, defStyleAttr, defStyleRes
    ) {
        initialize()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context!!, attrs, defStyleAttr
    ) {
        initialize()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(
        context!!, attrs
    ) {
        initialize()
    }

    constructor(context: Context?) : super(context!!) {
        initialize()
    }

    private fun initialize() {
        widgetLayoutResource = R.layout.preference_drop_down
    }

    override fun onBindViewHolder(view: PreferenceViewHolder) {
        super.onBindViewHolder(view)
        this.dropDownLabel = view.findViewById(R.id.drop_down_label) as TextView

        onViewReady?.invoke()
    }

    override fun setOnPreferenceClickListener(onPreferenceClickListener: OnPreferenceClickListener?) {
        this.clickListener = onPreferenceClickListener
    }

    fun setOnViewReady(init: (()->Unit)){
        this.onViewReady = init
    }

    override fun onClick() {
        if (clickListener == null || !clickListener!!.onPreferenceClick(this)) {
            super.onClick()
        }
    }

    fun setDropDownLabel(label: CharSequence?){
        dropDownLabel?.text = label
    }
}
