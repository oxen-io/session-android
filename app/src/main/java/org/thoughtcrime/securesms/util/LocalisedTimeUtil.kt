package org.thoughtcrime.securesms.util

import android.content.Context
import android.content.res.Configuration
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.commons.lang3.tuple.MutablePair
import org.session.libsignal.utilities.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

object LocalisedTimeUtil {

    private const val TAG = "LocalisedTimeUtil"

    // Keys for the map lookup
    private const val WEEK_KEY    = "Week"
    private const val WEEKS_KEY   = "Weeks"
    private const val DAY_KEY     = "Day"
    private const val DAYS_KEY    = "Days"
    private const val HOUR_KEY    = "Hour"
    private const val HOURS_KEY   = "Hours"
    private const val MINUTE_KEY  = "Minute"
    private const val MINUTES_KEY = "Minutes"
    private const val SECOND_KEY  = "Second"
    private const val SECONDS_KEY = "Seconds"

    // The map containing key->value pairs such as "Minutes" -> "minutos" and
    // "Hours" -> "horas" for Spanish etc.
    private var timeUnitMap: MutableMap<String, String> = mutableMapOf()

    // Extension property to extract the whole weeks from a given duration
    private val Duration.inWholeWeeks: Long
        get() { return this.inWholeDays.floorDiv(7) }

    private fun isRtlLanguage(context: Context): Boolean {
        return context.resources.configuration.layoutDirection == Configuration.SCREENLAYOUT_LAYOUTDIR_RTL
    }

    // Method to load the time string map for a given locale
    fun loadTimeStringMap(context: Context, locale: Locale) {
        // Attempt to load the appropriate time strings map based on the language code of our locale, i.e., "en" for English, "fr" for French etc.
        val filename = "csv/time_strings/time_strings_dict_" + locale.language + ".json"
        var inputStream: InputStream? = null
        try {
            inputStream = context.assets.open(filename)
        }
        catch (ioe: IOException) {
            Log.e(TAG, "Failed to open time string map file: $filename - falling back to English.", ioe)
            inputStream = context.assets.open("csv/time_strings/time_strings_dict_en.json")
        }
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        val jsonString = bufferedReader.use { it.readText() }
        timeUnitMap = Json.decodeFromString(jsonString)
    }

    // Method to get a locale-aware duration using the largest time unit in a given duration. For example
    // a duration of 3 hours and 7 minutes will return "3 hours" in English, or "3 horas" in Spanish.
    fun getDurationWithLargestTimeUnit(context: Context, duration: Duration): String {
        // Load the time string map if we haven't already
        if (timeUnitMap.isEmpty()) { loadTimeStringMap(context, Locale.getDefault()) }

        // Left is the duration value ("2" etc.) and right is the largest time unit string ("weeks" etc.)
        // Note: We always add a space when returning, band we'll flip the order if the language is RTL.
        var durationPair = MutablePair<String, String>()
        when {
            duration.inWholeWeeks > 0 -> {
                durationPair.left  = duration.inWholeWeeks.toString()
                durationPair.right = if (duration.inWholeWeeks == 1L) timeUnitMap[WEEK_KEY]
                                     else timeUnitMap[WEEKS_KEY]
            }
            duration.inWholeDays > 0 -> {
                durationPair.left  = duration.inWholeDays.toString()
                durationPair.right = if (duration.inWholeDays == 1L) timeUnitMap[DAY_KEY]
                                     else timeUnitMap[DAYS_KEY]
            }
            duration.inWholeHours > 0 -> {
                durationPair.left  = duration.inWholeHours.toString()
                durationPair.right = if (duration.inWholeHours == 1L) timeUnitMap[HOUR_KEY]
                                     else timeUnitMap[HOURS_KEY]
            }
            duration.inWholeMinutes > 0 -> {
                durationPair.left  = duration.inWholeMinutes.toString()
                durationPair.right = if (duration.inWholeMinutes == 1L) timeUnitMap[MINUTE_KEY]
                                     else timeUnitMap[MINUTES_KEY]
            }
            else -> {
                durationPair.left  = duration.inWholeSeconds.toString()
                durationPair.right = if (duration.inWholeSeconds == 1L) timeUnitMap[SECOND_KEY]
                                     else timeUnitMap[SECONDS_KEY]
            }
        }

        // Return the duration string in the correct order
        return if (!isRtlLanguage(context)) {
            durationPair.left + " " + durationPair.right
        } else {
            durationPair.right + " " + durationPair.left
        }
    }

    // Method to get a locale-aware duration using the two largest time units for a given duration. For example
    // a duration of 3 hours and 7 minutes will return "3 hours 7 minutes" in English, or "3 horas 7 minutos" in Spanish.
    fun getDurationWithLargestTwoTimeUnits(context: Context, duration: Duration): String {
        // Load the time string map if we haven't already
        if (timeUnitMap.isEmpty()) { loadTimeStringMap(context, Locale.getDefault()) }

        val isRTL = isRtlLanguage(context)

        // Assign our largest time period based on the duration. However, if the duration is less than a minute then
        // it messes up our large/small unit response because we don't do seconds and *milliseconds* - so we'll force
        // the use of minutes and seconds as a special case. Note: Durations cannot be negative so we don't have to check
        // <= 0L or anything.
        var smallTimePeriod = ""
        var bailFollowingSpecialCase = false
        var largeTimePeriod = when (duration.inWholeMinutes != 0L) {
            true -> getDurationWithLargestTimeUnit(context, duration)
            else -> {
                smallTimePeriod = getDurationWithLargestTimeUnit(context, duration)
                bailFollowingSpecialCase = true
                if (!isRTL) {
                    "${duration.inWholeMinutes} ${timeUnitMap[MINUTES_KEY]}" // i.e., "3 minutes"
                } else {
                    "${timeUnitMap[MINUTES_KEY]} ${duration.inWholeMinutes}" // i.e., "minutes 3"
                }
            }
        }

        // If we hit our special case of having to return big/small units for a sub-1-minute duration we can exit early,
        // otherwise we need to figure out the small unit before we can return it.
        if (bailFollowingSpecialCase) {
            return if (!isRTL) "$largeTimePeriod $smallTimePeriod" // i.e., "3 hours 7 minutes"
            else               "$smallTimePeriod $largeTimePeriod" // i.e., "minutes 7 hours 3"
        }

        if (duration.inWholeWeeks > 0) {
            // If the duration is more than a week then our small unit is days
            val durationMinusWeeks = duration.minus(7.days.times(duration.inWholeWeeks.toInt()))
            if (durationMinusWeeks.inWholeDays > 0) {
                smallTimePeriod = getDurationWithLargestTimeUnit(context, durationMinusWeeks)
            }
            else {
                smallTimePeriod = if (!isRTL) "0 ${timeUnitMap[DAYS_KEY]}"
                                  else        "${timeUnitMap[DAYS_KEY]} 0"
            }
        } else if (duration.inWholeDays > 0) {
            // If the duration is more than a day then our small unit is hours
            val durationMinusDays = duration.minus(1.days.times(duration.inWholeDays.toInt()))
            if (durationMinusDays.inWholeHours > 0) {
                smallTimePeriod = getDurationWithLargestTimeUnit(context, durationMinusDays)
            }
            else {
                smallTimePeriod = if (!isRTL) "0 ${timeUnitMap[HOURS_KEY]}"
                                  else        "${timeUnitMap[HOURS_KEY]} 0"
            }
        } else if (duration.inWholeHours > 0) {
            // If the duration is more than an hour then our small unit is minutes
            val durationMinusHours = duration.minus(1.hours.times(duration.inWholeHours.toInt()))
            if (durationMinusHours.inWholeMinutes > 0) {
                smallTimePeriod = getDurationWithLargestTimeUnit(context, durationMinusHours)
            }
            else {
                smallTimePeriod = if (!isRTL) "0 ${timeUnitMap[MINUTES_KEY]}"
                                  else        "${timeUnitMap[MINUTES_KEY]} 0"

            }
        } else if (duration.inWholeMinutes > 0) {
            // If the duration is more than a a minute then our small unit is seconds.
            // Note: We don't need to check if there are any seconds because it's our 'default' option
            val durationMinusMinutes = duration.minus(1.minutes.times(duration.inWholeMinutes.toInt()))
            smallTimePeriod = getDurationWithLargestTimeUnit(context, durationMinusMinutes)
        } else {
            Log.w(TAG, "We should never get here as a duration of sub-1-minute is handled by our special case block, above - falling back to use seconds as small unit.")
            val durationMinusMinutes = duration.minus(1.minutes.times(duration.inWholeMinutes.toInt()))
            smallTimePeriod = getDurationWithLargestTimeUnit(context, durationMinusMinutes)
        }

        // Return the pair of time durations in the correct order
        return if (!isRTL) "$largeTimePeriod $smallTimePeriod" // i.e., "3 hours 7 minutes"
        else               "$smallTimePeriod $largeTimePeriod" // i.e., "minutes 7 hours 3"

        //return if (!isRTL) Pair(largeTimePeriod, smallTimePeriod) // i.e., "3 hours 7 minutes"
//               else        Pair(smallTimePeriod, largeTimePeriod) // iei., "minutes 7 hours 3"
    }
}