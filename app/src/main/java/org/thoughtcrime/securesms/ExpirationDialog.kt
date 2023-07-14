package org.thoughtcrime.securesms

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import cn.carbswang.android.numberpickerview.library.NumberPickerView
import network.loki.messenger.R
import org.session.libsession.utilities.ExpirationUtil

fun Context.showExpirationDialog(
    expiration: Int,
    onExpirationTime: (Int) -> Unit
): AlertDialog {
    val view = LayoutInflater.from(this).inflate(R.layout.expiration_dialog, null)
    val numberPickerView = view.findViewById<NumberPickerView>(R.id.expiration_number_picker)

    fun updateText(index: Int) {
        view.findViewById<TextView>(R.id.expiration_details).text = when (index) {
            0 -> getString(R.string.ExpirationDialog_your_messages_will_not_expire)
            else -> getString(
                R.string.ExpirationDialog_your_messages_will_disappear_s_after_they_have_been_seen,
                numberPickerView.displayedValues[index]
            )
        }
    }

    val expirationTimes = resources.getIntArray(R.array.expiration_times)
    val expirationDisplayValues = expirationTimes
        .map { ExpirationUtil.getExpirationDisplayValue(this, it) }
        .toTypedArray()

    val selectedIndex = expirationTimes.run { indexOfFirst { it >= expiration }.coerceIn(indices) }

    numberPickerView.apply {
        displayedValues = expirationDisplayValues
        minValue = 0
        maxValue = expirationTimes.lastIndex
        setOnValueChangedListener { _, _, index -> updateText(index) }
        value = selectedIndex
    }

    updateText(selectedIndex)

    return showSessionDialog {
        title(getString(R.string.ExpirationDialog_disappearing_messages))
        view(view)
        okButton { onExpirationTime(numberPickerView.let { expirationTimes[it.value] }) }
        cancelButton()
    }
}
