package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import androidx.preference.CheckBoxPreference
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.thoughtcrime.securesms.ui.getSubbedCharSequence
import org.thoughtcrime.securesms.ui.getSubbedString

class SwitchPreferenceCompat : CheckBoxPreference {
    private var listener: OnPreferenceClickListener? = null

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context!!, attrs, defStyleAttr) {
        setLayoutRes()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context!!, attrs, defStyleAttr, defStyleRes) {
        setLayoutRes()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        setLayoutRes()
    }

    constructor(context: Context?) : super(context!!) {
        setLayoutRes()
    }

    private fun setLayoutRes() {
        widgetLayoutResource = R.layout.switch_compat_preference

        if (this.hasKey()) {
            val key = this.key

            // Substitute app name into lockscreen preference summary
            if (key.equals(LOCK_SCREEN_KEY, ignoreCase = true)) {
                val c = context
                val substitutedSummaryCS = c.getSubbedCharSequence(R.string.lockAppDescription, APP_NAME_KEY to c.getString(R.string.app_name))
                this.summary = substitutedSummaryCS
            }
        }
    }

    override fun setOnPreferenceClickListener(listener: OnPreferenceClickListener?) {
        this.listener = listener
    }

    override fun onClick() {
        if (listener == null || !listener!!.onPreferenceClick(this)) {
            super.onClick()
        }
    }

    companion object {
        private const val LOCK_SCREEN_KEY = "pref_android_screen_lock"
    }
}
