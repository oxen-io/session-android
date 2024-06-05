package org.session.libsession.utilities

import android.content.Context
import org.session.libsession.R
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import com.squareup.phrase.Phrase
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

fun Context.getExpirationTypeDisplayValue(sent: Boolean) = if (sent) getString(R.string.disappearingMessagesSent) else getString(R.string.disappearingMessagesRead)

object ExpirationUtil {

    // Keys for Phrase library substitution
    const val DAYS_KEY    = "days"
    const val HOURS_KEY   = "hours"
    const val MINUTES_KEY = "minutes"
    const val SECONDS_KEY = "seconds"
    const val WEEKS_KEY   = "weeks"

    @JvmStatic
    fun getExpirationDisplayValue(context: Context, duration: Duration): String = getExpirationDisplayValue(context, duration.inWholeSeconds.toInt())

    @JvmStatic
    fun getExpirationDisplayValue(context: Context, expirationTimeSecs: Int): String {
        return if (expirationTimeSecs <= 0) {
            context.getString(R.string.off)
        } else if (expirationTimeSecs < 1.minutes.inWholeSeconds) {
            // If expiration time is expressed in seconds
            if (expirationTimeSecs == 1) {
                Phrase.from(context, R.string.expirationSecond).put(SECONDS_KEY, expirationTimeSecs.toString()).format().toString()
            }
            else {
                Phrase.from(context, R.string.expirationSeconds).put(SECONDS_KEY, expirationTimeSecs.toString()).format().toString()
            }
        } else if (expirationTimeSecs < 1.hours.inWholeSeconds) {
            // If expiration time is expressed in minutes
            val minutes = (expirationTimeSecs / 1.minutes.inWholeSeconds).toInt()
            if (minutes == 1) {
                Phrase.from(context, R.string.expirationMinute).put(MINUTES_KEY, minutes.toString()).format().toString()
            }
            else {
                Phrase.from(context, R.string.expirationMinutes).put(MINUTES_KEY, minutes.toString()).format().toString()
            }
        } else if (expirationTimeSecs < 1.days.inWholeSeconds) {
            // If expiration time is expressed in hours
            val hours = (expirationTimeSecs / 1.hours.inWholeSeconds).toInt()
            if (hours == 1) {
                Phrase.from(context, R.string.expirationHour).put(HOURS_KEY, hours.toString()).format().toString()
            }
            else {
                Phrase.from(context, R.string.expirationHours).put(HOURS_KEY, hours.toString()).format().toString()
            }
        } else if (expirationTimeSecs < TimeUnit.DAYS.toSeconds(7)) {
            // If expiration time is expressed in days
            val days = (expirationTimeSecs / 1.days.inWholeSeconds).toInt()
            if (days == 1) {
                Phrase.from(context, R.string.expirationDay).put(DAYS_KEY, days.toString()).format().toString()
            }
            else {
                Phrase.from(context, R.string.expirationDays).put(DAYS_KEY, days.toString()).format().toString()
            }
        } else {
            // If expiration time is expressed in weeks
            val weeks = expirationTimeSecs / TimeUnit.DAYS.toSeconds(7).toInt()
            if (weeks == 1) {
                Phrase.from(context, R.string.expirationWeek).put(WEEKS_KEY, weeks.toString()).format().toString()
            }
            else {
                Phrase.from(context, R.string.expirationWeeks).put(WEEKS_KEY, weeks.toString()).format().toString()
            }

        }
    }

    fun getExpirationAbbreviatedDisplayValue(context: Context, expirationTimeSecs: Long) =
        if (expirationTimeSecs < TimeUnit.MINUTES.toSeconds(1)) {
            Phrase.from(context, R.string.expirationSecondsAbbreviated).put(SECONDS_KEY, expirationTimeSecs.toString()).format().toString()
        } else if (expirationTimeSecs < TimeUnit.HOURS.toSeconds(1)) {
            val minutes = expirationTimeSecs / TimeUnit.MINUTES.toSeconds(1)
            Phrase.from(context, R.string.expirationMinutesAbbreviated).put(MINUTES_KEY, minutes.toString()).format().toString()
        } else if (expirationTimeSecs < TimeUnit.DAYS.toSeconds(1)) {
            val hours = expirationTimeSecs / TimeUnit.HOURS.toSeconds(1)
            Phrase.from(context, R.string.expirationHoursAbbreviated).put(HOURS_KEY, hours.toString()).format().toString()
        } else if (expirationTimeSecs < TimeUnit.DAYS.toSeconds(7)) {
            val days = expirationTimeSecs / TimeUnit.DAYS.toSeconds(1)
            Phrase.from(context, R.string.expirationDaysAbbreviated).put(DAYS_KEY, days.toString()).format().toString()
        } else {
            val weeks = expirationTimeSecs / TimeUnit.DAYS.toSeconds(7)
            Phrase.from(context, R.string.expirationWeeksAbbreviated).put(WEEKS_KEY, weeks.toString()).format().toString()
        }

}
