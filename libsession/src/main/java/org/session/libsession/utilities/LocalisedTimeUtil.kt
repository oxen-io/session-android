package org.session.libsession

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.view.View
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

object LocalisedTimeUtil {
    private const val TAG = "LocalisedTimeUtil"

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
            if(secondsRemaining > 0) "${this.inWholeMinutes}m ${secondsRemaining}s"
            else "${this.inWholeMinutes}m"
        } else {
            "0m ${this.inWholeSeconds}s"
        }

    // Method to get a locale-aware duration string using the largest time unit in a given duration.
    // For example a duration of 3 hours and 7 minutes will return "3 hours" in English, or
    // "3 horas" in Spanish.
    fun getDurationWithSingleLargestTimeUnit(context: Context, duration: Duration): String {

        val locale = context.resources.configuration.locales[0]
        val format = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE)

        return when {
            duration.inWholeWeeks >= 1 -> {
                val weeks = duration.inWholeWeeks
                format.format(Measure(weeks, MeasureUnit.WEEK))
            }

            duration.inWholeDays >= 1 -> {
                val days = duration.inWholeDays
                format.format(Measure(days, MeasureUnit.DAY))
            }

            duration.inWholeHours >= 1 -> {
                val hours = duration.inWholeHours
                format.format(Measure(hours, MeasureUnit.HOUR))
            }

            duration.inWholeMinutes >= 1 -> {
                val minutes = duration.inWholeMinutes
                format.format(Measure(minutes, MeasureUnit.MINUTE))
            }
            else -> {
                val seconds = duration.inWholeSeconds
                format.format(Measure(seconds, MeasureUnit.SECOND))
            }
        }
    }

    // Method to get a locale-aware duration using the two largest time units for a given duration. For example
    // a duration of 3 hours and 7 minutes will return "3 hours 7 minutes" in English, or "3 horas 7 minutos" in Spanish.
    fun getDurationWithDualTimeUnits(context: Context, duration: Duration): String {
        val locale = context.resources.configuration.locales[0]
        val format = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE)

        val totalSeconds = duration.inWholeSeconds
        var remainingSeconds = totalSeconds

        val secondsInOneWeek   = 7.days.inWholeSeconds
        val secondsInOneDay    = 1.days.inWholeSeconds
        val secondsInOneHour   = 1.hours.inWholeSeconds
        val secondsInOneMinute = 1.minutes.inWholeSeconds

        val weeks = remainingSeconds / secondsInOneWeek
        remainingSeconds %= secondsInOneWeek

        val days = remainingSeconds / secondsInOneDay
        remainingSeconds %= secondsInOneDay

        val hours = remainingSeconds / secondsInOneHour
        remainingSeconds %= secondsInOneHour

        val minutes = remainingSeconds / secondsInOneMinute
        remainingSeconds %= secondsInOneMinute

        val seconds = remainingSeconds

        // Build a list of non-zero units
        val units = mutableListOf<Measure>()
        if (weeks > 0) {
            units.add(Measure(weeks, MeasureUnit.WEEK))
            units.add(Measure(days, MeasureUnit.DAY))
        } else if (days > 0) {
            units.add(Measure(days, MeasureUnit.DAY))
            units.add(Measure(hours, MeasureUnit.HOUR))
        } else if (hours > 0) {
            units.add(Measure(hours, MeasureUnit.HOUR))
            units.add(Measure(minutes, MeasureUnit.MINUTE))
        }
        else if (minutes > 0) {
            units.add(Measure(minutes, MeasureUnit.MINUTE))
            units.add(Measure(seconds, MeasureUnit.SECOND))
        }
        else {
            units.add(Measure(seconds, MeasureUnit.SECOND))
        }

        // Format the measures
        return format.formatMeasures(*units.toTypedArray())
    }
}