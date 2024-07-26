/*
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import org.session.libsignal.utilities.Log
import java.text.DateFormat.SHORT
import java.text.DateFormat.getTimeInstance
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// Enums used to get the locale-aware String for one of the three relative days
public enum class RelativeDay { TODAY, YESTERDAY, TOMORROW }

/**
 * Utility methods to help display dates in a nice, easily readable way.
 */
object DateUtils : android.text.format.DateUtils() {

    @Suppress("unused")
    private val TAG: String = DateUtils::class.java.simpleName
    private val DAY_PRECISION_DATE_FORMAT = SimpleDateFormat("yyyyMMdd")
    private val HOUR_PRECISION_DATE_FORMAT = SimpleDateFormat("yyyyMMddHH")

    private fun isWithin(millis: Long, span: Long, unit: TimeUnit): Boolean {
        return System.currentTimeMillis() - millis <= unit.toMillis(span)
    }

    private fun isYesterday(`when`: Long): Boolean {
        return isToday(`when` + TimeUnit.DAYS.toMillis(1))
    }

    private fun convertDelta(millis: Long, to: TimeUnit): Int {
        return to.convert(System.currentTimeMillis() - millis, TimeUnit.MILLISECONDS).toInt()
    }

    // Method to get the String for a relative day in a locale-aware fashion
    public fun getLocalisedRelativeDayString(relativeDay: RelativeDay): String {

        val now = Calendar.getInstance()

        // To compare a time to 'now' we need to use get a date relative it, so plus or minus a day, or not
        val dayAddition = when (relativeDay) {
            RelativeDay.TOMORROW  -> {  1 }
            RelativeDay.YESTERDAY -> { -1 }
            else -> 0 // Today
        }

        val comparisonTime = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayAddition)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return getRelativeTimeSpanString(comparisonTime.timeInMillis,
            now.timeInMillis,
            DAY_IN_MILLIS,
            FORMAT_SHOW_DATE).toString()
    }

    // Method to get the locale-aware String for the word "Now"
//    public fun getLocalisedNowString(): String {
//        val now = Calendar.getInstance().timeInMillis
//        return getRelativeTimeSpanString(now, now,MINUTE_IN_MILLIS, FORMAT_SHOW_TIME).toString()
//    }

    // THIS DOES NOT WORK
    public fun getLocalisedNowString(): String {

        val now = Calendar.getInstance().timeInMillis
        val relativeTime = getRelativeTimeSpanString(now, now, MINUTE_IN_MILLIS, FORMAT_SHOW_TIME).toString()

        // Create a DateFormat instance for the current time
        val timeFormat = getTimeInstance(SHORT, Locale.getDefault())
        val formattedTime = timeFormat.format(Calendar.getInstance().time)

        // Check if the relative time indicates "0 minutes ago" or similar and replace it with the formatted time
        return if (relativeTime == "0 minutes ago" || relativeTime == "in 0 minutes") {
            formattedTime
        } else {
            relativeTime
        }
    }

    fun getFormattedDateTime(time: Long, template: String, locale: Locale): String {
        val localizedPattern = getLocalizedPattern(template, locale)
        return SimpleDateFormat(localizedPattern, locale).format(Date(time))
    }

    fun getHourFormat(c: Context?): String {
        return if ((DateFormat.is24HourFormat(c))) "HH:mm" else "hh:mm a"
    }

    fun getDisplayFormattedTimeSpanString(c: Context, locale: Locale, timestamp: Long): String {
        // If the timestamp is invalid (ie. 0) then assume we're waiting on data and just use the 'Now' copy
        return if (timestamp == 0L || isWithin(timestamp, 1, TimeUnit.MINUTES)) {
            getLocalisedNowString()
            //c.getString(R.string.DateUtils_just_now) // ACL REMOVE WHEN HAPPY
        } else if (isToday(timestamp)) {
            getFormattedDateTime(timestamp, getHourFormat(c), locale)
        } else if (isWithin(timestamp, 6, TimeUnit.DAYS)) {
            getFormattedDateTime(timestamp, "EEE " + getHourFormat(c), locale)
        } else if (isWithin(timestamp, 365, TimeUnit.DAYS)) {
            getFormattedDateTime(timestamp, "MMM d " + getHourFormat(c), locale)
        } else {
            getFormattedDateTime(timestamp, "MMM d " + getHourFormat(c) + ", yyyy", locale)
        }
    }

    fun getDetailedDateFormatter(context: Context?, locale: Locale): SimpleDateFormat {
        val dateFormatPattern = if (DateFormat.is24HourFormat(context)) {
            getLocalizedPattern("MMM d, yyyy HH:mm:ss zzz", locale)
        } else {
            getLocalizedPattern("MMM d, yyyy hh:mm:ss a zzz", locale)
        }

        return SimpleDateFormat(dateFormatPattern, locale)
    }

    // Method to get the String for a relative day in a locale-aware fashion, including using the
    // auto-localised words for "today" and "yesterday" as appropriate.
    fun getRelativeDate(
        context: Context,
        locale: Locale,
        timestamp: Long
    ): String {
        return if (isToday(timestamp)) {
            getLocalisedRelativeDayString(RelativeDay.TODAY)
            //context.getString(R.string.DateUtils_today) // ACL REMOVE WHEN HAPPY
        } else if (isYesterday(timestamp)) {
            getLocalisedRelativeDayString(RelativeDay.YESTERDAY)
            //context.getString(R.string.DateUtils_yesterday) // ACL REMOVE WHEN HAPPY
        } else {
            getFormattedDateTime(timestamp, "EEE, MMM d, yyyy", locale)
        }
    }

    fun isSameDay(t1: Long, t2: Long): Boolean {
        return DAY_PRECISION_DATE_FORMAT.format(Date(t1)) == DAY_PRECISION_DATE_FORMAT.format(Date(t2))
    }

    fun isSameHour(t1: Long, t2: Long): Boolean {
        return HOUR_PRECISION_DATE_FORMAT.format(Date(t1)) == HOUR_PRECISION_DATE_FORMAT.format(Date(t2))
    }

    private fun getLocalizedPattern(template: String, locale: Locale): String {
        return DateFormat.getBestDateTimePattern(locale, template)
    }

    /**
     * e.g. 2020-09-04T19:17:51Z
     * https://www.iso.org/iso-8601-date-and-time-format.html
     *
     * @return The timestamp if able to be parsed, otherwise -1.
     */
    @SuppressLint("ObsoleteSdkInt")
    @JvmStatic
    public fun parseIso8601(date: String?): Long {

        if (date.isNullOrEmpty()) { return -1 }

        val format = if (Build.VERSION.SDK_INT >= 24) {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.getDefault())
        } else {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
        }

        try {
            return format.parse(date).time
        } catch (e: ParseException) {
            Log.w(TAG, "Failed to parse date.", e)
            return -1
        }
    }
}