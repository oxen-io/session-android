package org.session.libsession

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import junit.framework.TestCase
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Test
import org.junit.runner.RunWith
import org.session.libsession.LocalisedTimeUtil.toShortTwoPartString
import org.session.libsignal.utilities.Log

import android.text.format.DateUtils
import kotlin.math.abs


/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class LocalisedTimeStringTests {
    private val TAG = "LocalisedTimeStringsTest"

    // Whether or not to print debug info during the test - can be useful
    private val printDebug = true

    val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    // Test durations
    private val oneSecond                = 1.seconds
    private val twoSeconds               = 2.seconds
    private val oneMinute                = 1.minutes
    private val twoMinutes               = 2.minutes
    private val oneHour                  = 1.hours
    private val twoHours                 = 2.hours
    private val oneDay                   = 1.days
    private val twoDays                  = 2.days
    private val oneDaySevenHours         = 1.days.plus(7.hours)
    private val fourDaysTwentyThreeHours = 4.days.plus(23.hours)
    private val oneWeekTwoDays           = 9.days
    private val twoWeekTwoDays           = 16.days

    // List of the above for each loop-based comparisons
    private val allDurations = listOf(
        oneSecond,
        twoSeconds,
        oneMinute,
        twoMinutes,
        oneHour,
        twoHours,
        oneDay,
        twoDays,
        oneDaySevenHours,
        fourDaysTwentyThreeHours,
        oneWeekTwoDays,
        twoWeekTwoDays
    )

    // Method to get the localised time as the single largest time unit in the duration, e.g.,
    //  - 90.minutes -> 1 hour
    //  - 30.hours   -> 1 day
    //  - 170.hours  -> 1 week
    private fun performSingleTimeUnitStringComparison(expectedOutputsList: List<String>) {
        for (i in 0 until allDurations.count()) {
            var txt = LocalisedTimeUtil.getDurationWithSingleLargestTimeUnit(context, allDurations[i])
            if (printDebug) println("$i: Single time unit - expected: ${expectedOutputsList[i]} - got: $txt")
            TestCase.assertEquals(expectedOutputsList[i], txt)
        }
    }

    // Method to get the localised time as the two largest time units in the duration, e.g.,
    //  - 90.minutes -> 1 hour 30 minutes
    //  - 30.hours   -> 1 day 6 hours
    //  - 170.hours  -> 1 week 0 days
    private fun performDualTimeUnitStringComparison(expectedOutputsList: List<String>) {
        for (i in 0 until allDurations.count()) {
            val txt = LocalisedTimeUtil.getDurationWithDualTimeUnits(context, allDurations[i])
            if (printDebug) println("$i: Dual time units - expected: ${expectedOutputsList[i]} - got: $txt")
            TestCase.assertEquals(expectedOutputsList[i], txt)
        }
    }

    @Test
    fun testShortTimeDurations() {
        // Non-localised short time descriptions. Note: We never localise shortened durations like these.
        val shortTimeDescriptions = listOf(
            "0m 1s",
            "0m 2s",
            "1m 0s",
            "2m 0s",
            "1h 0m",
            "2h 0m",
            "1d 0h",
            "2d 0h",
            "1d 7h",
            "4d 23h",
            "1w 2d",
            "2w 2d"
        )

        for (i in 0 until shortTimeDescriptions.count()) {
            val txt = allDurations[i].toShortTwoPartString()
            if (printDebug) println("Short time strings - expected: ${shortTimeDescriptions[i]} - got: $txt")
            TestCase.assertEquals(shortTimeDescriptions[i], txt)
        }
    }

    fun getRelativeTimeLocalized(timestampMS: Long): String {
        // Get the current system time
        val nowMS = System.currentTimeMillis()

        // Calculate the time difference in milliseconds - this value will be negative if it's in the
        // future or positive if it's in the past.
        val timeDifferenceMS = nowMS - timestampMS

        // Choose a desired time resolution based on the time difference.
        // Note: We do this against the absolute time difference so this function can still work for
        // both future/past times without having separate future/past cases.
        val desiredResolution = when (abs(timeDifferenceMS)) {
            in 0..DateUtils.MINUTE_IN_MILLIS -> DateUtils.SECOND_IN_MILLIS
            in DateUtils.MINUTE_IN_MILLIS..DateUtils.HOUR_IN_MILLIS -> DateUtils.MINUTE_IN_MILLIS
            in DateUtils.HOUR_IN_MILLIS..DateUtils.DAY_IN_MILLIS    -> DateUtils.HOUR_IN_MILLIS
            in DateUtils.DAY_IN_MILLIS..DateUtils.WEEK_IN_MILLIS    -> DateUtils.DAY_IN_MILLIS

            // We don't do months or years, so if the result is 53 weeks then so be it - also, the
            // getRelativeTimeSpanString method's resolution maxes out at weeks!
            else -> DateUtils.WEEK_IN_MILLIS
        }

        // Get the system locale
        val locale = Locale.getDefault()

        // Use DateUtils to get the relative time span string
        return DateUtils.getRelativeTimeSpanString(
            timestampMS,
            nowMS,
            desiredResolution,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_RELATIVE // Try this w/ just FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    @Test
    fun testSystemGeneratedRelativeTimes() {
        var t = 0L

        // 1 and 2 seconds ago
        t = System.currentTimeMillis() - 1.seconds.inWholeMilliseconds
        print(getRelativeTimeLocalized(t))
        t = System.currentTimeMillis() - 2.seconds.inWholeMilliseconds
        print(getRelativeTimeLocalized(t))

        // 1 and 2 minutes ago
        t = System.currentTimeMillis() - 1.minutes.inWholeMilliseconds
        print(getRelativeTimeLocalized(t))
        t = System.currentTimeMillis() - 2.minutes.inWholeMilliseconds
        print(getRelativeTimeLocalized(t))

        // 1 and 2 hours ago
        t = System.currentTimeMillis() - 1.hours.inWholeMilliseconds
        print(getRelativeTimeLocalized(t))
        t = System.currentTimeMillis() - 2.hours.inWholeMilliseconds
        print(getRelativeTimeLocalized(t))

        assert(true)
    }

    // Unit test for durations in the English language. Note: We can pre-load the time-units string
    // map via `LocalisedTimeUtil.loadTimeStringMap`, or alternatively they'll get loaded for the
    // current context / locale on first use.
    @Test
    fun timeSpanStrings_EN() {
        // Expected single largest time unit outputs for English
        // Note: For all single largest time unit durations we may discard smaller time unit information as appropriate.
        val expectedOutputsSingleEN = listOf(
            "1 second",  // 1 second
            "2 seconds", // 2 seconds
            "1 minute",  // 1 minute
            "2 minutes", // 2 minutes
            "1 hour",    // 1 hour
            "2 hours",   // 2 hours
            "1 day",     // 1 day
            "2 days",    // 2 days
            "1 day",     // 1 day   7 hours as single unit is: 1 day
            "4 days",    // 4 days 23 hours as single unit is: 4 days
            "1 week",    // 1 week  2 days  as single unit is: 1 week
            "2 weeks"    // 2 weeks 2 days  as single unit is: 2 weeks
        )

        // Expected dual largest time unit outputs for English
        val expectedOutputsDualEN = listOf(
            "0 minutes 1 second",   // 1 second
            "0 minutes 2 seconds",  // 2 seconds
            "1 minute 0 seconds",   // 1 minute
            "2 minutes 0 seconds",  // 2 minutes
            "1 hour 0 minutes",     // 1 hour
            "2 hours 0 minutes",    // 2 hours
            "1 day 0 hours",        // 1 day
            "2 days 0 hours",       // 2 days
            "1 day 7 hours",        // 1 day 7 hours
            "4 days 23 hours",      // 4 days 23 hours
            "1 week 2 days",        // 1 week 2 days
            "2 weeks 2 days"        // 2 weeks 2 days
        )

        Locale.setDefault(Locale.ENGLISH)
        if (printDebug) Log.w(TAG, "EN tests - current locale is: " + Locale.getDefault())
        performSingleTimeUnitStringComparison(expectedOutputsSingleEN)
        performDualTimeUnitStringComparison(expectedOutputsDualEN)
    }

    // Unit test for durations in the French language
    @Test
    fun timeSpanStrings_FR() {
        // Expected single largest time unit outputs for French
        val expectedOutputsSingle_FR = listOf(
            "1 seconde",    // 1 second
            "2 secondes",   // 2 seconds
            "1 minute",     // 1 minute
            "2 minutes",    // 2 minutes
            "1 heure",      // 1 hour
            "2 heures",     // 2 hours
            "1 jour",       // 1 day
            "2 jours",      // 2 days
            "1 jour",       // 1 day   7 hours as single unit is: 1 day
            "4 jours",      // 4 days 23 hours as single unit is: 4 days
            "1 semaine",    // 1 week  2 days  as single unit is: 1 week
            "2 semaines"    // 2 weeks 2 days  as single unit is: 2 weeks
        )

        // Expected dual largest time unit outputs for French
        val expectedOutputsDual_FR = listOf(
            "0 minutes 1 seconde",  // 1 second
            "0 minutes 2 secondes", // 2 seconds
            "1 minute 0 secondes",  // 1 minute
            "2 minutes 0 secondes", // 2 minutes
            "1 heure 0 minutes",    // 1 hour
            "2 heures 0 minutes",   // 2 hours
            "1 jour 0 heures",      // 1 day
            "2 jours 0 heures",     // 2 days
            "1 jour 7 heures",      // 1 day 7 hours
            "4 jours 23 heures",    // 4 days 23 hours
            "1 semaine 2 jours",    // 1 week 2 days
            "2 semaines 2 jours"    // 2 weeks 2 days
        )

        Locale.setDefault(Locale.FRENCH)
        if (printDebug) Log.w(TAG, "FR tests - current locale is: " + Locale.getDefault())
        performSingleTimeUnitStringComparison(expectedOutputsSingle_FR)
        performDualTimeUnitStringComparison(expectedOutputsDual_FR)
    }

    // Method to reverse the order of words in a string, separating on spaces.
    // It is genuinely easier to do this with the RTL strings that fight through the chaos that is
    // trying to use a mixed LTR/RTL mode in Android Studio, which is quite frankly a nightmare.
    //
    // Also: If you find yourself fighting with RTL stuff you can disable it in the Android Studio
    // editor by finding your `idea.properties` file and adding the line `editor.disable.rtl=true`
    // My file was at: ~/.local/share/JetBrains/Toolbox/apps/android-studio-2/bin/idea.properties
    private fun String.reverseWordOrder(): String {
        return this.split(" ").reversed().joinToString(" ")
    }

    // Unit test for durations in the Arabic language
    @Test
    fun timeSpanStrings_AR() {
        // Expected single largest time unit outputs for Arabic.
        //
        // Note: As Arabic is a Right-to-Left language the number goes on the right!
        //
        // Also: This is not a PERFECT mapping to the correct time unit plural phrasings for Arabic
        // because they have six separate time units based on 0, 1..2, 3..9, 11.12, 21..99, as well
        // as round values of 10 in the range 10..90 (i.e., 10, 20, 30, ..., 80, 90). Our custom
        // time unit phrases only handle singular & plural - so we're not going to be perfect, but
        // we'll be good enough to get our point across, like if you said to me "See you in 3 day"
        // I'd know that you means "See you in 3 dayS".
        // Further reading: https://www.fluentarabic.net/numbers-in-arabic/
        val expectedOutputsSingleAR = listOf(
            "1 ثانية".reverseWordOrder(), // 1 second
            "2 ثانية".reverseWordOrder(), // 2 seconds
            "1 دقيقة".reverseWordOrder(), // 1 minute
            "2 دقائق".reverseWordOrder(), // 2 minutes
            "1 ساعة".reverseWordOrder(),  // 1 hour
            "2 ساعات".reverseWordOrder(), // 2 hours
            "1 يوم".reverseWordOrder(),   // 1 day
            "2 أيام".reverseWordOrder(),  // 2 days
            "1 يوم".reverseWordOrder(),   // 1 day 7 hours as single unit (1 day)
            "4 أيام".reverseWordOrder(),  // 4 days 23 hours as single unit (4 days)
            "1 أسبوع".reverseWordOrder(), // 1 week 2 days as single unit (1 week)
            "2 أسابيع".reverseWordOrder() // 2 weeks 2 days as single unit (2 weeks)
        )

        // Arabic dual unit times (largest time unit is on the right!)
        val expectedOutputsDualAR = listOf(
            "0 دقائق 1 ثانية".reverseWordOrder(), // 0 minutes 1 second
            "0 دقائق 2 ثانية".reverseWordOrder(), // 0 minutes 2 seconds
            "1 دقيقة 0 ثانية".reverseWordOrder(), // 1 minute 0 seconds
            "2 دقائق 0 ثانية".reverseWordOrder(), // 2 minutes 0 seconds
            "1 ساعة 0 دقائق".reverseWordOrder(),  // 1 hour 0 minutes
            "2 ساعات 0 دقائق".reverseWordOrder(), // 2 hours 0 minutes
            "1 يوم 0 ساعات".reverseWordOrder(),   // 1 day 0 hours
            "2 أيام 0 ساعات".reverseWordOrder(),  // 2 days 0 hours
            "1 يوم 7 ساعات".reverseWordOrder(),   // 1 day 7 hours as single unit (1 day)
            "4 أيام 23 ساعات".reverseWordOrder(), // 4 days 23 hours as single unit (4 days)
            "1 أسبوع 2 أيام".reverseWordOrder(),  // 1 week 2 days as single unit (1 week)
            "2 أسابيع 2 أيام".reverseWordOrder()  // 2 weeks 2 days as single unit (2 weeks)
        )

        Locale.setDefault(Locale.forLanguageTag("ar"))
        if (printDebug) Log.w(TAG, "AR tests - current locale is: " + Locale.getDefault())

        // Just changing the context language won't result in the app being in RTL mode so we'll
        // force LocalisedTimeUtils to respond in RTL mode just for this instrumented test.
        LocalisedTimeUtil.forceUseOfRtlForTests(true)

        performSingleTimeUnitStringComparison(expectedOutputsSingleAR)
        performDualTimeUnitStringComparison(expectedOutputsDualAR)
    }

    // Unit test for durations in the Japanese language
    @Test
    fun timeSpanStrings_JA() {
        // Expected single largest time unit outputs for Japanese.
        // Note: The plural for multiple days below is technically incorrect because we only get
        // the added symbol (日間) for 5 days and above - but this will be correct more of the time
        // than just using the '1..4 days' symbol (日) for day plurals.
        val expectedOutputsSingle_JA = listOf(
            "1 秒",    // 1 second
            "2 秒",    // 2 seconds
            "1 分",    // 1 minute
            "2 分",    // 2 minutes
            "1 時間",  // 1 hour
            "2 時間",  // 2 hours
            "1 日",    // 1 day
            "2 日間",  // 2 days.
            "1 日",   // 1 day   7 hours as single unit is: 1 day
            "4 日間", // 4 days 23 hours as single unit is: 4 days
            "1 週間", // 1 week  2 days  as single unit is: 1 week
            "2 週間"  // 2 weeks 2 days  as single unit is: 2 weeks
        )

        // Expected dual largest time unit outputs for Japanese
        val expectedOutputsDual_JA = listOf(
            "0 分 1 秒",      // 1 second
            "0 分 2 秒",      // 2 seconds
            "1 分 0 秒",      // 1 minute
            "2 分 0 秒",      // 2 minutes
            "1 時間 0 分",    // 1 hour
            "2 時間 0 分",    // 2 hours
            "1 日 0 時間",    // 1 day
            "2 日間 0 時間",  // 2 days
            "1 日 7 時間",    // 1 day 7 hours
            "4 日間 23 時間", // 4 days 23 hours
            "1 週間 2 日間",  // 1 week 2 days
            "2 週間 2 日間"   // 2 weeks 2 days
        )

        Locale.setDefault(Locale.forLanguageTag("ja"))
        if (printDebug) Log.w(TAG, "JA tests - current locale is: " + Locale.getDefault())

        performSingleTimeUnitStringComparison(expectedOutputsSingle_JA)
        performDualTimeUnitStringComparison(expectedOutputsDual_JA)
    }

    // Unit test for durations in the Urdu language (RTL language)
    @Test
    fun timeSpanStrings_UR() {
        // Expected single largest time unit outputs for Urdu
        val expectedOutputsSingle_UR = listOf(
            "1 سیکنڈ".reverseWordOrder(), // 1 second
            "2 سیکنڈ".reverseWordOrder(), // 2 seconds
            "1 منٹ".reverseWordOrder(),   // 1 minute
            "2 منٹ".reverseWordOrder(),   // 2 minutes
            "1 گھنٹہ".reverseWordOrder(), // 1 hour
            "2 گھنٹے".reverseWordOrder(), // 2 hours
            "1 دن".reverseWordOrder(),    // 1 day
            "2 دن".reverseWordOrder(),    // 2 days.
            "1 دن".reverseWordOrder(),    // 1 day   7 hours as single unit is: 1 day
            "4 دن".reverseWordOrder(),    // 4 days 23 hours as single unit is: 4 days
            "1 ہفتہ".reverseWordOrder(),  // 1 week  2 days  as single unit is: 1 week
            "2 ہفتے".reverseWordOrder()   // 2 weeks 2 days  as single unit is: 2 weeks
        )

        // Expected dual largest time unit outputs for Urdu
        val expectedOutputsDual_UR = listOf(
            "0 منٹ 1 سیکنڈ".reverseWordOrder(), // 1 second -> 0 minutes 1 second
            "0 منٹ 2 سیکنڈ".reverseWordOrder(), // 2 seconds -> 0 minutes 2 seconds
            "1 منٹ 0 سیکنڈ".reverseWordOrder(), // 1 minute -> 1 minute 0 seconds
            "2 منٹ 0 سیکنڈ".reverseWordOrder(), // 2 minutes -> 2 minutes 0 seconds
            "1 گھنٹہ 0 منٹ".reverseWordOrder(), // 1 hour -> 1 hour 0 minutes
            "2 گھنٹے 0 منٹ".reverseWordOrder(), // 2 hours -> 2 hours 0 minutes
            "1 دن 0 گھنٹے".reverseWordOrder(),  // 1 day -> 1 day 0 hours
            "2 دن 0 گھنٹے".reverseWordOrder(),  // 2 days -> 2 days 0 hours
            "1 دن 7 گھنٹے".reverseWordOrder(),  // 1 day 7 hours
            "4 دن 23 گھنٹے".reverseWordOrder(), // 4 days 23 hours
            "1 ہفتہ 2 دن".reverseWordOrder(),   // 1 week 2 days
            "2 ہفتے 2 دن".reverseWordOrder()    // 2 weeks 2 days
        )

        Locale.setDefault(Locale.forLanguageTag("ur"))
        if (printDebug) Log.w(TAG, "UR tests - current locale is: " + Locale.getDefault())

        // Just changing the context language won't result in the app being in RTL mode so we'll
        // force LocalisedTimeUtils to respond in RTL mode just for this instrumented test.
        LocalisedTimeUtil.forceUseOfRtlForTests(true)

        performSingleTimeUnitStringComparison(expectedOutputsSingle_UR)
        performDualTimeUnitStringComparison(expectedOutputsDual_UR)
    }
}
