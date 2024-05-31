package org.session.libsession.utilities

import android.content.Context
import org.session.libsession.R
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import com.squareup.phrase.Phrase

fun Context.getExpirationTypeDisplayValue(sent: Boolean) = if (sent) getString(R.string.disappearingMessagesSent) else getString(R.string.disappearingMessagesRead)

object ExpirationUtil {

    // Keys for Phrase library substitution
    const val HOURS_KEY = "hours"
    const val MINUTES_KEY = "minutes"
    const val SECONDS_KEY = "seconds"

    @JvmStatic
    fun getExpirationDisplayValue(context: Context, duration: Duration): String = getExpirationDisplayValue(context, duration.inWholeSeconds.toInt())

    @JvmStatic
    fun getExpirationDisplayValue(context: Context, expirationTime: Int): String {
        return if (expirationTime <= 0) {
            context.getString(R.string.off)
        } else if (expirationTime < TimeUnit.MINUTES.toSeconds(1)) {
            context.resources.getQuantityString(
                R.plurals.expiration_seconds,
                expirationTime,
                expirationTime
            )
        } else if (expirationTime < TimeUnit.HOURS.toSeconds(1)) {
            val minutes = expirationTime / TimeUnit.MINUTES.toSeconds(1).toInt()
            context.resources.getQuantityString(R.plurals.expiration_minutes, minutes, minutes)
        } else if (expirationTime < TimeUnit.DAYS.toSeconds(1)) {
            val hours = expirationTime / TimeUnit.HOURS.toSeconds(1).toInt()
            context.resources.getQuantityString(R.plurals.expiration_hours, hours, hours)
        } else if (expirationTime < TimeUnit.DAYS.toSeconds(7)) {
            val days = expirationTime / TimeUnit.DAYS.toSeconds(1).toInt()
            context.resources.getQuantityString(R.plurals.expiration_days, days, days)
        } else {
            val weeks = expirationTime / TimeUnit.DAYS.toSeconds(7).toInt()
            context.resources.getQuantityString(R.plurals.expiration_weeks, weeks, weeks)
        }
    }

    fun getExpirationAbbreviatedDisplayValue(context: Context, expirationTime: Long): String {
        return if (expirationTime < TimeUnit.MINUTES.toSeconds(1)) {
            Phrase.from(context, R.string.expirationSecondsAbbreviated).put(SECONDS_KEY, expirationTime.toString()).format().toString()
        } else if (expirationTime < TimeUnit.HOURS.toSeconds(1)) {
            val minutes = expirationTime / TimeUnit.MINUTES.toSeconds(1)
            context.resources.getString(R.string.expiration_minutes_abbreviated, minutes)
        } else if (expirationTime < TimeUnit.DAYS.toSeconds(1)) {
            val hours = expirationTime / TimeUnit.HOURS.toSeconds(1)
            context.resources.getString(R.string.expiration_hours_abbreviated, hours)
        } else if (expirationTime < TimeUnit.DAYS.toSeconds(7)) {
            val days = expirationTime / TimeUnit.DAYS.toSeconds(1)
            context.resources.getString(R.string.expiration_days_abbreviated, days)
        } else {
            val weeks = expirationTime / TimeUnit.DAYS.toSeconds(7)
            context.resources.getString(R.string.expiration_weeks_abbreviated, weeks)
        }
    }
}
