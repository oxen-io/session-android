package org.thoughtcrime.securesms.preferences.widgets

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceCategory

class SessionPreferenceCategory @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null): PreferenceCategory(context, attrs) {

    init {

    }

    override fun addItemFromInflater(preference: Preference?) {
        super.addItemFromInflater(preference)
    }
}