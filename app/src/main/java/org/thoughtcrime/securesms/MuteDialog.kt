package org.thoughtcrime.securesms

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.LocalisedTimeUtil
import org.session.libsession.utilities.StringSubstitutionConstants.TIME_LARGE_KEY
import org.session.libsignal.utilities.Log
import java.time.Duration
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
            val largeTimeUnitText = LocalisedTimeUtil.getDurationWithSingleLargestTimeUnit(context, Option.entries[index].getTime().milliseconds)
            Phrase.from(context, entry.stringRes)
                .put(TIME_LARGE_KEY, largeTimeUnitText)
                .format().toString()
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

/*
package org.thoughtcrime.securesms

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.LocalContext
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.LocalisedTimeUtil
import org.session.libsession.utilities.StringSubstitutionConstants.TIME_LARGE_KEY
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

private fun getTimestampOfNowPlusDuration(menuItemIndex: Int): (Int) -> Unit {
    when (menuItemIndex) {
        0 -> System.currentTimeMillis() + 1.hours.inWholeMilliseconds
        1 -> System.currentTimeMillis() + 2.hours.inWholeMilliseconds
        2 -> System.currentTimeMillis() + 1.days.inWholeMilliseconds
        3 -> System.currentTimeMillis() + 7.days.inWholeMilliseconds
        else Long.MAX_VALUE
    }
}

fun showMuteDialog(
    context: Context,
    onMuteDuration: (Long) -> Unit
): AlertDialog {
    val c = context

    val oneHourString = Phrase.from(c, R.string.notificationsMuteFor)
        .put(TIME_LARGE_KEY, LocalisedTimeUtil.getDurationWithSingleLargestTimeUnit(c, 1.hours))
        .format().toString()
    val oneHourOption:Pair<String, Long> =  oneHourString to System.currentTimeMillis() + 1.hours.inWholeMilliseconds

    val twoHoursString = Phrase.from(c, R.string.notificationsMuteFor)
        .put(TIME_LARGE_KEY, LocalisedTimeUtil.getDurationWithSingleLargestTimeUnit(c, 2.hours))
        .format().toString()
    val twoHoursOption:Pair<String, Long> =  twoHoursString to System.currentTimeMillis() + 2.hours.inWholeMilliseconds

    val oneDayString = Phrase.from(c, R.string.notificationsMuteFor)
        .put(TIME_LARGE_KEY, LocalisedTimeUtil.getDurationWithSingleLargestTimeUnit(c, 1.days))
        .format().toString()
    val oneDayOption:Pair<String, Long> =  twoHoursString to System.currentTimeMillis() + 1.days.inWholeMilliseconds

    val oneWeekString = Phrase.from(c, R.string.notificationsMuteFor)
        .put(TIME_LARGE_KEY, LocalisedTimeUtil.getDurationWithSingleLargestTimeUnit(c, 7.days))
        .format().toString()
    val oneWeekOption:Pair<String, Long> =  twoHoursString to System.currentTimeMillis() + 7.days.inWholeMilliseconds

    val foreverString = c.getString(R.string.notificationsMute)
    val foreverOption = foreverString to Long.MAX_VALUE

    val allStrings = arrayOf(oneHourString, twoHoursString, oneDayString, oneWeekString, foreverString)

    val allOptions = listOf(oneHourOption, twoHoursOption, oneDayOption, oneWeekOption, foreverOption)

    val foo: (Int) -> Unit = { getTimestampOfNowPlusDuration(3) }

    context.showSessionDialog {
        title(R.string.notificationsMute)

        //items(allStrings, onMuteDuration(getTimestampOfNowPlusDuration(allStrings.val))

        items(allOptions.map { it.first }.toTypedArray() { allOptions.entries[it].getTime() } )



        items(allOptions.map { it.first }.map(it.)) { it.second } Option.entries.map { it.stringRes }.map(
            Phrase.from(context, R.string.notificationsMuteFor)
                .put(TIME_LARGE_KEY, LocalisedTimeUtil.getDurationWithSingleLargestTimeUnit(context, it.getTime())..format().toString()toType,

                    context::getString).toTypedArray()) {
            onMuteDuration(Option.entries[it].getTime())
        }
    }
}

private enum class Option(@StringRes val stringRes: Int, val getTime: () -> Long) {

    ONE_HOUR(R.string.notificationsMuteFor, duration = 1.hours.inWholeMilliseconds),
    TWO_HOURS(R.string.notificationsMuteFor, duration = 2.hours.inWholeMilliseconds),
    ONE_DAY(R.string.notificationsMuteFor, duration = 1.days.inWholeMilliseconds),
    SEVEN_DAYS(R.string.notificationsMuteFor, duration = 7.days.inWholeMilliseconds),
    FOREVER(R.string.notificationsMute, getTime = { Long.MAX_VALUE });

    constructor(@StringRes stringRes: Int, duration: Long): this(stringRes, { System.currentTimeMillis() + duration })
}
*/