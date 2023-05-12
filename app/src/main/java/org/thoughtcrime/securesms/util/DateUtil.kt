package org.thoughtcrime.securesms.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import network.loki.messenger.R
import org.thoughtcrime.securesms.util.DateUtils.isSameDay
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class DateUtil (
    private val context: Context,
    private val clock: Clock
) {
    @Inject constructor(@ApplicationContext context: Context): this(context, AndroidClock)

    /**
     * If the message was sent/received on the same day we show: 12 hour time + am/pm
     *   12:43 pm
     * If the message was sent/received on the same week we show: word abbreviation day of week + 12 hour time + am/pm
     *   Tue 12:34 pm
     * If the message was sent/received in the current year we show, word abbreviation of month, date (day) and 12 hour time + am/pm
     *   Mar 9 12:34 pm
     * If the message was sent or received outside of the current year, then we show dd/mm/yy
     *   09/03/23
     */
    @JvmOverloads
    fun getDisplayFormattedTimeSpanString(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        if (isWithin(timestamp, 1, TimeUnit.MINUTES)) {
            return context.getString(R.string.DateUtils_just_now)
        }
        return when {
            isToday(timestamp) -> context.hourFormat
            isWithin(timestamp, 6, TimeUnit.DAYS) -> "EEE " + context.hourFormat
            isThisYear(timestamp) -> "MMM d " + context.hourFormat
            else -> "MMM d " + context.hourFormat + ", yyyy"
        }.let { DateUtils.getFormattedDateTime(timestamp, it, locale) }
    }

    private fun isThisYear(millis: Long): Boolean = isSameYear(millis, clock.currentTimeMillis)

    fun isToday(time: Long): Boolean = isSameDay(time, clock.currentTimeMillis)

    fun isWithin(millis: Long, span: Long, unit: TimeUnit): Boolean =
        clock.currentTimeMillis - millis <= unit.toMillis(span)
}

fun isSameYear(millis: Long, millisOther: Long): Boolean = Calendar.getInstance().run {
        apply { timeInMillis = millis }.get(Calendar.YEAR) == apply { timeInMillis = millisOther }.get(Calendar.YEAR)
    }

private val Context.hourFormat: String get() = DateUtils.getHourFormat(this)
