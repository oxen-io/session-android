package org.session.libsession

import android.content.Context
import android.view.View
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.session.libsignal.utilities.Log
import java.io.BufferedReader
import java.io.IOException
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

    // Prefix & suffix for the specific language / locale that we're loading time strings for
    private const val TIME_STRINGS_PATH_PREFIX = "json/time_strings/time_strings_dict_"
    private const val TIME_STRINGS_PATH_SUFFIX = ".json"

    // If we couldn't open the specific time strings map then we'll fall back to English
    private const val FALLBACK_ENGLISH_TIME_STRINGS = "json/time_strings/time_strings_dict_en.json"

    // The map containing key->value pairs such as "Minutes" -> "minutos" and
    // "Hours" -> "horas" for Spanish etc.
    private var timeUnitMap: MutableMap<String, String> = mutableMapOf()

    // Extension property to extract the whole weeks from a given duration
    private val Duration.inWholeWeeks: Long
        get() { return this.inWholeDays.floorDiv(7) }

    // Instrumented tests don't fire up the app in RTL mode when we change the context so we have to
    // force RTL mode for languages such as Arabic.
    private var forcedRtl = false
    fun forceUseOfRtlForTests(value: Boolean) { forcedRtl = value }

    fun isRtlLanguage(context: Context) =
        context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL || forcedRtl

    fun isLtrLanguage(context: Context): Boolean =
        context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR && !forcedRtl

    // Method to get shortened two-part strings for durations like "2h 14m"
    // Note: As designed, we do not provide localisation for shortened strings.
    // Also: We'll provide durations like "0m 30s" for 30s as this is a "toShortTwoPartString"
    // method - if we really want _just_ "30s" or similar for durations less than 1 minute then we
    // can create a "toShortString" method, otherwise the name of this method and what it actually
    // does are at odds.
    fun Duration.toShortTwoPartString(): String =
        if (this.inWholeWeeks > 0) {
            val daysRemaining = this.minus(7.days.times(this.inWholeWeeks.toInt())).inWholeDays
            "${this.inWholeWeeks}w ${daysRemaining}d"
        } else if (this.inWholeDays > 0) {
            val hoursRemaining = this.minus(1.days.times(this.inWholeDays.toInt())).inWholeHours
            "${this.inWholeDays}d ${hoursRemaining}h"
        } else if (this.inWholeHours > 0) {
            val minutesRemaining = this.minus(1.hours.times(this.inWholeHours.toInt())).inWholeMinutes
            "${this.inWholeHours}h ${minutesRemaining}m"
        } else if (this.inWholeMinutes > 0) {
            val secondsRemaining = this.minus(1.minutes.times(this.inWholeMinutes.toInt())).inWholeSeconds
            "${this.inWholeMinutes}m ${secondsRemaining}s"
        } else {
            "0m ${this.inWholeSeconds}s"
        }

    // Method to load the time string map for a given locale
    fun loadTimeStringMap(context: Context, locale: Locale) {
        // Attempt to load the appropriate time strings map based on the language code of our locale, i.e., "en" for English, "fr" for French etc.
        val filename = TIME_STRINGS_PATH_PREFIX + locale.language + TIME_STRINGS_PATH_SUFFIX
        val inputStream = try {
            context.assets.open(filename)
        }
        catch (ioe: IOException) {
            Log.e(TAG, "Failed to open time string map file: $filename - falling back to English.", ioe)
            context.assets.open(FALLBACK_ENGLISH_TIME_STRINGS)
        }

        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        val jsonString = bufferedReader.use { it.readText() }
        timeUnitMap = Json.decodeFromString(jsonString)
    }

    // Method to get a locale-aware duration string using the largest time unit in a given duration.
    // For example a duration of 3 hours and 7 minutes will return "3 hours" in English, or
    // "3 horas" in Spanish.
    fun getDurationWithSingleLargestTimeUnit(context: Context, duration: Duration): String {
        // Load the time string map if we haven't already
        if (timeUnitMap.isEmpty()) { loadTimeStringMap(context, Locale.getDefault()) }

        // Build up the key/value string map. First is always our number (i.e. 3), and Second is
        // always our time unit (i.e., "weeks" or "hours" or whatever).
        var durationMap = when {
            duration.inWholeWeeks > 0L -> {
                duration.inWholeWeeks.toString() to
                if (duration.inWholeWeeks == 1L) timeUnitMap[WEEK_KEY]
                else timeUnitMap[WEEKS_KEY]
            }

            duration.inWholeDays > 0 -> {
                duration.inWholeDays.toString() to
                if (duration.inWholeDays == 1L) timeUnitMap[DAY_KEY]
                else timeUnitMap[DAYS_KEY]
            }

            duration.inWholeHours > 0 -> {
                duration.inWholeHours.toString() to
                if (duration.inWholeHours == 1L) timeUnitMap[HOUR_KEY]
                else timeUnitMap[HOURS_KEY]
            }

            duration.inWholeMinutes > 0 -> {
                duration.inWholeMinutes.toString() to
                if (duration.inWholeMinutes == 1L) timeUnitMap[MINUTE_KEY]
                else timeUnitMap[MINUTES_KEY]
            }

            else -> {
                duration.inWholeSeconds.toString() to
                if (duration.inWholeSeconds == 1L) timeUnitMap[SECOND_KEY]
                else timeUnitMap[SECONDS_KEY]
            }
        }

        // Return the duration string in the correct order
        return if (isLtrLanguage(context)) {
            durationMap.first + " " + durationMap.second // e.g., "3 minutes"
        } else {
            durationMap.second + " " + durationMap.first // e.g., "minutes 3"
        }
    }

    // Method to get a locale-aware duration using the two largest time units for a given duration. For example
    // a duration of 3 hours and 7 minutes will return "3 hours 7 minutes" in English, or "3 horas 7 minutos" in Spanish.
    fun getDurationWithDualTimeUnits(context: Context, duration: Duration): String {
        // Load the time string map for the currently locale if we haven't already
        if (timeUnitMap.isEmpty()) { loadTimeStringMap(context, Locale.getDefault()) }

        val isLTR = isLtrLanguage(context)

        // Assign our largest time period based on the duration. However, if the duration is less than a minute then
        // it messes up our large/small unit response because we don't do seconds and *milliseconds* - so we'll force
        // the use of minutes and seconds as a special case. Note: Durations cannot be negative so we don't have to check
        // <= 0L or anything.
        var smallTimePeriod = ""
        var bailFollowingSpecialCase = false
        var largeTimePeriod = if (duration.inWholeMinutes > 0L) {
            getDurationWithSingleLargestTimeUnit(context, duration)
        } else {
            // If seconds is the largest time period
            smallTimePeriod = getDurationWithSingleLargestTimeUnit(context, duration)
            bailFollowingSpecialCase = true
            if (isLTR) {
                "0 ${timeUnitMap[MINUTES_KEY]}" // i.e., large time period will be "0 minutes" in the current language
            } else {
                "${timeUnitMap[MINUTES_KEY]} 0" // i.e., large time period will be "minutes 0" in the current language
            }
        }

        // If we hit our special case of having to return big/small units for a sub-1-minute duration we can exit early,
        // otherwise we need to figure out the small unit before we can return it.
        if (bailFollowingSpecialCase) {
            return if (isLTR) "$largeTimePeriod $smallTimePeriod" // i.e., "3 hours 7 minutes"
            else              "$smallTimePeriod $largeTimePeriod" // i.e., "minutes 7 hours 3"
        }

        if (duration.inWholeWeeks > 0) {
            // If the duration is more than a week then our small unit is days
            val durationMinusWeeks = duration.minus(7.days.times(duration.inWholeWeeks.toInt()))
            if (durationMinusWeeks.inWholeDays > 0) {
                smallTimePeriod = getDurationWithSingleLargestTimeUnit(context, durationMinusWeeks)
            }
            else {
                smallTimePeriod = if (isLTR) "0 ${timeUnitMap[DAYS_KEY]}"
                                  else       "${timeUnitMap[DAYS_KEY]} 0"
            }

        } else if (duration.inWholeDays > 0) {
            // If the duration is more than a day then our small unit is hours
            val durationMinusDays = duration.minus(1.days.times(duration.inWholeDays.toInt()))
            if (durationMinusDays.inWholeHours > 0) {
                smallTimePeriod = getDurationWithSingleLargestTimeUnit(context, durationMinusDays)
            }
            else {
                smallTimePeriod = if (isLTR) "0 ${timeUnitMap[HOURS_KEY]}"
                                  else       "${timeUnitMap[HOURS_KEY]} 0"
            }

        } else if (duration.inWholeHours > 0) {
            // If the duration is more than an hour then our small unit is minutes
            val durationMinusHours = duration.minus(1.hours.times(duration.inWholeHours.toInt()))
            if (durationMinusHours.inWholeMinutes > 0) {
                smallTimePeriod = getDurationWithSingleLargestTimeUnit(context, durationMinusHours)
            }
            else {
                smallTimePeriod = if (isLTR) "0 ${timeUnitMap[MINUTES_KEY]}"
                                  else       "${timeUnitMap[MINUTES_KEY]} 0"
            }

        } else if (duration.inWholeMinutes > 0) {
            // If the duration is more than a a minute then our small unit is seconds.
            // Note: We don't need to check if there are any seconds because it's our 'default' option
            val durationMinusMinutes = duration.minus(1.minutes.times(duration.inWholeMinutes.toInt()))
            smallTimePeriod = getDurationWithSingleLargestTimeUnit(context, durationMinusMinutes)
        } else {
            Log.w(TAG, "We should never get here as a duration of sub-1-minute is handled by our special case block, above - falling back to use seconds as small unit.")
            val durationMinusMinutes = duration.minus(1.minutes.times(duration.inWholeMinutes.toInt()))
            smallTimePeriod = getDurationWithSingleLargestTimeUnit(context, durationMinusMinutes)
        }

        // Return the pair of time durations in the correct order
        return if (isLTR) "$largeTimePeriod $smallTimePeriod" // i.e., "3 hours 7 minutes"
        else              "$smallTimePeriod $largeTimePeriod" // i.e., "minutes 7 hours 3"
    }
}