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
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// Enums used to get the locale-aware String for one of the three relative days
enum class RelativeDay { TODAY, YESTERDAY, TOMORROW }

/**
 * Utility methods to help display dates in a nice, easily readable way.
 */
object DateUtils : android.text.format.DateUtils() {

    @Suppress("unused")
    private val TAG: String = DateUtils::class.java.simpleName
    private val DAY_PRECISION_DATE_FORMAT = SimpleDateFormat("yyyyMMdd")
    private val HOUR_PRECISION_DATE_FORMAT = SimpleDateFormat("yyyyMMddHH")

    // Preferred date and time formats for the user - these are stored in the user's preferences.
    // We set them as invalid on startup (range is 0..8 and 0..2 respectively) so we only have to
    // retrieve the preference once rather than every time we wish to format a date or time.
    // See: TextSecurePreferences.DATE_FORMAT_PREF and TextSecurePreferences for further details.
    // Note: For testing you can just set these values to something which isn't -1 and it will
    // bypass the retrieve-from-pref operation and use the associated format.
    private var userPreferredTimeFormat: Int = -1
    private var userPreferredDateFormat: Int = 7

    // String for the actual date format pattern that `userPreferredDataFormat` equates to - we'll
    // start it off as a sane default, and update it to either follow the system or a specific
    // format as the user desires.
    private var userPreferredDateFormatPattern = "dd/MM/YYYY"

    private fun isWithin(millis: Long, span: Long, unit: TimeUnit): Boolean {
        return System.currentTimeMillis() - millis <= unit.toMillis(span)
    }

    private fun isYesterday(`when`: Long): Boolean {
        return isToday(`when` + TimeUnit.DAYS.toMillis(1))
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

        val temp = getRelativeTimeSpanString(
            comparisonTime.timeInMillis,
            now.timeInMillis,
            DAY_IN_MILLIS,
            FORMAT_SHOW_DATE).toString()
        return temp
    }

    // THIS IS THE METHOD THAT ACTUALLY GETS USED TO GET THE DATE TIME
    fun getFormattedDateTime(timestamp: Long, template: String, locale: Locale): String {

        Log.w("ACL", "Getting formatted datetime - template is: " + template)
        val localizedPattern = getLocalizedPattern(template, locale)
        Log.w("ACL", "Which turns into localizedPattern: " + localizedPattern)
        return SimpleDateFormat(template, locale).format(Date(timestamp))
    }

    // Method to get the user's preferred time format, whether that's 12-hour or 24-hour
    fun getHourFormat(c: Context): String {
        // If this is the first run..
        if (userPreferredTimeFormat == -1) {
            // ..update our preferred time format (will return -1 if no saved pref).
            userPreferredTimeFormat = TextSecurePreferences.getTimeFormatPref(c)

            // If no saved value was written we'll write 0 for "Follow system setting" - this will only run on first install
            if (userPreferredTimeFormat == -1) {
                userPreferredTimeFormat = 0
                TextSecurePreferences.setTimeFormatPref(c, userPreferredTimeFormat)
            }

            // If the preferred time format is "Follow system setting" then we need to find out what the system setting is!
            if (userPreferredTimeFormat == 0) {
                val is24HourFormat = DateFormat.is24HourFormat(c)

                // Set the time format we'll use to either 24 or 12 hours.
                // IMPORTANT: We don't WRITE this to the pref - we just use it while the app is running!
                // Note: See TextSecurePreferences.TIME_FORMAT_PREF for further details of available formats.
                userPreferredTimeFormat = if (is24HourFormat) 2 else 1
            }
        }

        // At this point userPreferredTimeFormat will ALWAYS be either 1 or 2 - regardless of if the saved
        // pref is 0 to "Follow system settings".
        return if (userPreferredTimeFormat == 1) "hh:mm a" else "HH:mm"
    }

    // Method to take the SimpleDateFormat.getPattern() string and set the user's preferred date format appropriately
    private fun updateDateFormatSettingsFromPattern(dateFormatPattern: String) {
        when (dateFormatPattern) {
            "M/d/yy"     -> { userPreferredDateFormat = 1; userPreferredDateFormatPattern = dateFormatPattern }
            "d/M/yy"     -> { userPreferredDateFormat = 2; userPreferredDateFormatPattern = dateFormatPattern }
            "dd/MM/yyyy" -> { userPreferredDateFormat = 3; userPreferredDateFormatPattern = dateFormatPattern }
            "dd.MM.yyyy" -> { userPreferredDateFormat = 4; userPreferredDateFormatPattern = dateFormatPattern }
            "dd-MM-yyyy" -> { userPreferredDateFormat = 5; userPreferredDateFormatPattern = dateFormatPattern }
            "yyyy/M/d"   -> { userPreferredDateFormat = 6; userPreferredDateFormatPattern = dateFormatPattern }
            "yyyy.M.d"   -> { userPreferredDateFormat = 7; userPreferredDateFormatPattern = dateFormatPattern }
            "yyyy-M-d"   -> { userPreferredDateFormat = 8; userPreferredDateFormatPattern = dateFormatPattern }
            else         -> {
                // Sane fallback for unrecognised date format patten
                userPreferredDateFormat = 3; userPreferredDateFormatPattern = "dd/MM/yyyy"
            }
        }
    }

    // Method to take an int from the user's dateFormatPref and update our date format pattern accordingly
    private fun updateDateFormatSettingsFromInt(dateFormatPrefInt: Int) {
        var mutableDateFormatPrefInt = dateFormatPrefInt
        // There's probably a more elegant way to do this - but we can't use `.also { }` because
        // the else block
        when (mutableDateFormatPrefInt) {
            1 -> { userPreferredDateFormatPattern = "M/d/yy"     }
            2 -> { userPreferredDateFormatPattern = "d/M/yy"     }
            3 -> { userPreferredDateFormatPattern = "dd/MM/yyyy" }
            4 -> { userPreferredDateFormatPattern = "dd.MM.yyyy" }
            5 -> { userPreferredDateFormatPattern = "dd-MM-yyyy" }
            6 -> { userPreferredDateFormatPattern = "yyyy/M/d"   }
            7 -> { userPreferredDateFormatPattern = "yyyy.M.d"   }
            8 -> { userPreferredDateFormatPattern = "yyyy-M-d"   }
            else -> {
                // Sane fallback for unrecognised date format pattern ("dd/MM/yyyy")
                Log.w("DateUtils", "Bad dateFormatPrefInt - falling back to dd/MM/yyyy")
                mutableDateFormatPrefInt = 3 // Because we 'also' update our setting from this!
                userPreferredDateFormatPattern = "dd/MM/yyyy"
            }
        }.also {
            // Typically we pass in `userPreferredDataFormat` TO this method - but because we perform
            // sanitisation in the case of a bad value we'll also write a cleaned version back to the var.
            userPreferredDateFormat = mutableDateFormatPrefInt
        }
    }
    
    private fun isWithinOneWeek(timestamp: Long) = System.currentTimeMillis() - timestamp <= TimeUnit.DAYS.toMillis(7)
    private fun isWithinOneYear(timestamp: Long) = System.currentTimeMillis() - timestamp <= TimeUnit.DAYS.toMillis(365)

    fun getDisplayFormattedTimeSpanString(c: Context, locale: Locale, timestamp: Long): String {
        // Note: Date patterns are in TR-35 format.
        // See: https://www.unicode.org/reports/tr35/tr35-dates.html#Date_Format_Patterns
        val t = if (isToday(timestamp)) {
            Log.w("ACL", "Within today")
            // If it's within the last 24 hours we just give the time in 24-hour format, such as "13:27" for 1:27pm
            getFormattedDateTime(timestamp, getHourFormat(c), locale)
        } else if (isWithinOneWeek(timestamp)) {
            Log.w("ACL", "Within week")
            // If it's within the last week we give the day as 3 letters then the time in 24-hour format, such as "Fri 13:27" for Friday 1:27pm
            getFormattedDateTime(timestamp, "EEE " + getHourFormat(c), locale)
        } else if (isWithinOneYear(timestamp)) {
            Log.w("ACL", "Within year")
            // If it's within the last year we give the month as 3 letters then the time in 24-hour format, such as "Mar 13:27" for March 1:27pm
            // CAREFUL: "MMM d + getHourFormat(c)" actually turns out to be "8 July, 17:14" etc. - it is DAY-NUMBER and then MONTH (which can go up to 4 chars) - and THEN the time. Wild.
            getFormattedDateTime(timestamp, "MMM d " + getHourFormat(c), locale)
        } else {
            // NOTE: The `userPreferredDateFormat` is ONLY ever used on dates which exceed one year!
            // See the Figma linked in ticket SES-360 for details.

            Log.w("ACL", "More than 1 year")
            // If the date is more than a year ago then we get "19 July 2023, 16:19" type format

            // If the app has just started..
            if (userPreferredDateFormat == -1) {
                // ..update our preferred date format (will return -1 if no saved pref).
                userPreferredDateFormat = TextSecurePreferences.getDateFormatPref(c)

                // If we don't have a saved date format pref we'll write 0 for "Follow system setting".
                // Note: This will only execute on first install & run where the pref doesn't exist.
                if (userPreferredDateFormat == -1) {
                    userPreferredDateFormat = 0
                    TextSecurePreferences.setDateFormatPref(c, userPreferredDateFormat)
                }
            }

            // --- At this point we will always have _some_ preferred date format ---

            // If the preferred date format is "Follow system setting" then we need to find out what the system setting is!
            if (userPreferredDateFormat == 0) {
                val dateFormat = DateFormat.getDateFormat(c)

                // Check if the DateFormat instance is a SimpleDateFormat
                if (dateFormat is SimpleDateFormat) {
                    val dateFormatPattern = dateFormat.toLocalizedPattern()
                    Log.w("ACL", "Date pattern: " + dateFormat.toPattern())

                    // System setting has a date format pattern? Cool - let's use it, whatever it might be
                    userPreferredDateFormatPattern = dateFormatPattern

                    // Update our userPreferredDateFormat & pattern from the system setting
                    //updateDateFormatSettingsFromPattern(dateFormatPattern)
                } else {
                    // If the dateFormat ISN'T a SimpleDateFormat from which we can extract a pattern then the best
                    // we can do is pick a sensible default like dd/MM/YYYY - which equates to option 3 out of our
                    // available options (see TextSecurePreferences.DATE_FORMAT_PREF for further details).
                    userPreferredDateFormat = 3
                    userPreferredDateFormatPattern = "dd/MM/yyyy"
                }

                // IMPORTANT: As we've updated the `userPreferredDataFormat` from "follow system setting" to
                // "whatever that system setting is" we DO NOT write that back to the pref - we leave the
                // saved value as is so that it always uses that system settings, whatever that may be.
            } else {
                // If the user has asked for a specific date format that isn't "Follow system setting"
                // then update our date formatting settings from that preference.
                updateDateFormatSettingsFromInt(userPreferredDateFormat)
            }

            //userPreferredDateFormatPattern
            getFormattedDateTime(timestamp, userPreferredDateFormatPattern, locale)
            //getFormattedDateTime(timestamp, "MMM d " + getHourFormat(c) + ", yyyy", locale)
        }

        Log.w("ACL", "t is: $t")
        return t

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
        } else if (isYesterday(timestamp)) {
            getLocalisedRelativeDayString(RelativeDay.YESTERDAY)
        } else {
            getFormattedDateTime(timestamp, userPreferredDateFormatPattern, locale)
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
}