package org.thoughtcrime.securesms

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import network.loki.messenger.R
import org.session.libsession.LocalisedTimeUtil
import org.session.libsession.utilities.StringSubstitutionConstants.TIME_LARGE_KEY
import org.thoughtcrime.securesms.ui.getSubbedString
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun showMuteDialog(
    context: Context,
    onMuteDuration: (Long) -> Unit
): AlertDialog = context.showSessionDialog {
    title(R.string.notificationsMute)

    items(Option.entries.mapIndexed { index, entry ->

        if (entry.stringRes == R.string.notificationsMute) {
            context.getString(R.string.notificationsMute)
        } else {
            val largeTimeUnitString = LocalisedTimeUtil.getDurationWithSingleLargestTimeUnit(context, Option.entries[index].getTime().milliseconds)
            context.getSubbedString(entry.stringRes, TIME_LARGE_KEY to largeTimeUnitString)
        }
    }.toTypedArray()) {
        // Note: We add the current timestamp to the mute duration to get the un-mute timestamp
        // that gets stored in the database via ConversationMenuHelper.mute().
        // Also: This is a kludge, but we ADD one second to the mute duration because otherwise by
        // the time the view for how long the conversation is muted for gets set then it's actually
        // less than the entire duration - so 1 hour becomes 59 minutes, 1 day becomes 23 hours etc.
        // As we really want to see the actual set time (1 hour / 1 day etc.) then we'll bump it by
        // 1 second which is neither here nor there in the grand scheme of things.
        onMuteDuration(Option.entries[it].getTime() + System.currentTimeMillis() + 1.seconds.inWholeMilliseconds)
    }
}

private enum class Option(@StringRes val stringRes: Int, val getTime: () -> Long) {
    ONE_HOUR(R.string.notificationsMuteFor,   duration = TimeUnit.HOURS.toMillis(1)),
    TWO_HOURS(R.string.notificationsMuteFor,  duration = TimeUnit.HOURS.toMillis(2)),
    ONE_DAY(R.string.notificationsMuteFor,    duration = TimeUnit.DAYS.toMillis(1)),
    SEVEN_DAYS(R.string.notificationsMuteFor, duration = TimeUnit.DAYS.toMillis(7)),
    FOREVER(R.string.notificationsMute, getTime = { Long.MAX_VALUE } );

    constructor(@StringRes stringRes: Int, duration: Long): this(stringRes, { duration } )
}