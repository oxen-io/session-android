package org.thoughtcrime.securesms.preferences

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import org.thoughtcrime.securesms.showSessionDialog

fun listPreferenceDialog(
    context: Context,
    listPreference: ListPreference,
    onChange: () -> Unit
) : AlertDialog = listPreference.run {
    context.showSessionDialog {
        val index = entryValues.indexOf(value)
        val options = entries.map(CharSequence::toString).toTypedArray()

        title(dialogTitle)
        text(dialogMessage)
        singleChoiceItems(options, index) {
            listPreference.setValueIndex(it)
            onChange()
        }
    }
}
