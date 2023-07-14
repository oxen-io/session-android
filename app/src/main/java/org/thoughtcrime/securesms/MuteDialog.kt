package org.thoughtcrime.securesms

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import network.loki.messenger.R
import java.util.concurrent.TimeUnit

fun showMuteDialog(
    context: Context,
    onMuteDuration: (Long) -> Unit
): AlertDialog = context.showSessionDialog {
    title(R.string.MuteDialog_mute_notifications)
    items(Option.values().map { it.stringRes }.map(context::getString).toTypedArray()) {
        onMuteDuration(Option.values()[it].getTime())
    }
}

private enum class Option(@StringRes val stringRes: Int, val getTime: () -> Long) {
    ONE_HOUR(R.string.arrays__mute_for_one_hour, duration = TimeUnit.HOURS.toMillis(1)),
    TWO_HOURS(R.string.arrays__mute_for_two_hours, duration = TimeUnit.DAYS.toMillis(2)),
    ONE_DAY(R.string.arrays__mute_for_one_day, duration = TimeUnit.DAYS.toMillis(1)),
    SEVEN_DAYS(R.string.arrays__mute_for_seven_days, duration = TimeUnit.DAYS.toMillis(7)),
    FOREVER(R.string.arrays__mute_forever, getTime = { Long.MAX_VALUE });

    constructor(@StringRes stringRes: Int, duration: Long): this(stringRes, { System.currentTimeMillis() + duration })
}
