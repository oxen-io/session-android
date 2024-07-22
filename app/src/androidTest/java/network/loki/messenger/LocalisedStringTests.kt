package network.loki.messenger

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import junit.framework.TestCase.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Test
import org.junit.runner.RunWith
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.util.LocalisedTimeUtil

@RunWith(AndroidJUnit4::class)
@SmallTest
class LocalisedStringTests {

    private val TAG = "LocalisedStringTests"

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

    // Short descriptions of each duration.
    // Note: When using short durations like this we don't localise them.
    private val shortTimeDescriptions = listOf(
        "1s",
        "2s",
        "1m",
        "2m",
        "1h",
        "2h",
        "1d",
        "2d",
        "1d7h",
        "4d23h",
        "1w2d",
        "2w2d"
    )

    // Expected single largest time unit outputs for English
    // Note: For all single largest time unit durations we may discard smaller time unit information as appropriate.
    private val expectedOutputsSingle_EN = listOf(
        "1 second",     // 1 second
        "2 seconds",    // 2 seconds
        "1 minute",     // 1 minute
        "2 minutes",    // 2 minutes
        "1 hour",       // 1 hour
        "2 hours",      // 2 hours
        "1 day",        // 1 day
        "2 days",       // 2 days
        "1 day",        // 1 day   7 hours as single unit is: 1 day
        "4 days",       // 4 days 23 hours as single unit is: 4 days
        "1 week",       // 1 week  2 days  as single unit is: 1 week
        "2 weeks"       // 2 weeks 2 days  as single unit is: 2 weeks
    )

    // Expected dual largest time unit outputs for English
    private val expectedOutputsDual_EN = listOf(
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

    // Expected single largest time unit outputs for French
    private val expectedOutputsSingle_FR = listOf(
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
    private val expectedOutputsDual_FR = listOf(
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

    // Expected single largest time unit outputs for Arabic
    // Note: As Arabic is a Right-to-Left language the number goes on the right!
    // Also: This is not a PERFECT mapping to the correct time unit plural phrasings for Arabic
    // because they have six separate time units based on 0, 1..2, 3..9, 11.12, 21..99, as well as
    // round values of 10 in the range 10..90 (i.e., 10, 20, 30, ..., 80, 90). Our custom time unit
    // phrases only handle singular & plural - so we're not going to be perfect, but we'll be good
    // enough to get our point across, like if you said to me "I'm going on holiday for 7 day" I'd
    // know what you meant even though you didn't says "7 dayS".
    // Further reading: https://www.fluentarabic.net/numbers-in-arabic/
    private val expectedOutputsSingle_AR = listOf("1 ثانية",    // 1 second
                                                  "2 ثانية",    // 2 seconds
                                                  "1 دقيقة",    // 1 minute
                                                  "2 دقائق",    // 2 minutes
                                                  "1 ساعة",     // 1 hour
                                                  "2 ساعات",    // 2 hours
                                                  "1 يوم",      // 1 day
                                                  "2 أيام",     // 2 days
                                                  "1 يوم",      // 1 day 7 hours as single unit (1 day)
                                                  "4 أيام",     // 4 days 23 hours as single unit (4 days)
                                                  "1 أسبوع",   // 1 week 2 days as single unit (1 week)
                                                  "2 أسابيع")   // 2 weeks 2 days as single unit (2 weeks)

    // Arabic dual unit times (largest time unit is on the right!)
    private val expectedOutputsDual_AR =     listOf("0 دقائق 1 ثانية",  // 0 minutes 1 second
                                                    "0 دقائق 2 ثانية",  // 0 minutes 2 seconds
                                                    "1 دقيقة 0 ثانية",  // 1 minute 0 seconds
                                                    "2 دقيقة 0 ثانية",  // 2 minutes 0 seconds // ACL HEREEEEEEEEE!
                                                    "1 ساعة",   // 1 hour
                                                    "2 ساعات",  // 2 hours
                                                    "1 يوم",    // 1 day
                                                    "2 أيام",   // 2 days
                                                    "1 يوم",    // 1 day 7 hours as single unit (1 day)
                                                    "4 أيام",   // 4 days 23 hours as single unit (4 days)
                                                    "1 أسبوع",  // 1 week 2 days as single unit (1 week)
                                                    "2 أسابيع") // 2 weeks 2 days as single unit (2 weeks)


    val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ApplicationContext


    // The map containing key->value pairs such as "Minutes" -> "minutos" and "Hours" -> "horas" for Spanish etc.
    //var timeUnitStringsMap: Map<String, String> = loadTimeStringMap(context, Locale.ENGLISH)


    // Method to load the time string map for a given locale
    /*
    private fun loadTimeStringMap(context: Context, locale: Locale): Map<String, String> {
        // Attempt to load the appropriate time strings map based on the language code of our locale, i.e., "en" for English, "fr" for French etc.
        val filename = "csv/time_string_maps/time_strings_dict_" + locale.language + ".json"
        var inputStream: InputStream? = null
        try {
            inputStream = context.assets.open(filename)
        }
        catch (ioe: IOException) {
            Log.e(TAG, "Failed to open time string map file: $filename - attempting to use English!", ioe)
            inputStream = context.assets.open("csv/time_string_maps/time_strings_dict_en.json")
        }
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        val jsonString = bufferedReader.use { it.readText() }
        return Json.decodeFromString(jsonString)
    }
    */

    // ACL Note: This `to2partString` method is from Andy and will print things like "2h 14m" but it does NOT localise at all!
    /*
    private fun Duration.to2partString(): String? =
        toComponents { days, hours, minutes, seconds, nanoseconds -> listOf(days.days, hours.hours, minutes.minutes, seconds.seconds) }
            .filter { it.inWholeSeconds > 0L }.take(2).takeIf { it.isNotEmpty() }?.joinToString(" ")
    */

    // Note: For locales from language codes:
    // - Arabic is "ar"
    // - Japanese is "ja"
    // - Urdu is "ur"


    private fun performSingleTimeUnitStringComparison(expectedOutputsList: List<String>, printDebug: Boolean = false) {
        val lastIndex = allDurations.count() - 1
        for (i in 0..lastIndex) {
            var txt = LocalisedTimeUtil.getDurationWithSingleLargestTimeUnit(context, allDurations[i])
            if (printDebug) println("Single time unit - expected: ${expectedOutputsList[i]} - got: $txt")
            assertEquals(expectedOutputsList[i], txt)
        }
    }

    private fun performDualTimeUnitStringComparison(expectedOutputsList: List<String>, printDebug: Boolean = false) {
        val lastIndex = allDurations.count() - 1
        for (i in 0..lastIndex) {
            var txt = LocalisedTimeUtil.getDurationWithDualTimeUnits(context, allDurations[i])
            if (printDebug) println("Dual time units - expected: ${expectedOutputsList[i]} - got: $txt")
            assertEquals(expectedOutputsList[i], txt)
        }
    }

    // Unit test for durations in the English language. Note: We can pre-load the time-units string
    // map via `LocalisedTimeUtil.loadTimeStringMap`, or alternatively they'll get loaded for the
    // current context / locale on first use.
    @Test
    fun time_span_strings_EN(printDebug: Boolean = true) {
        Locale.setDefault(Locale.ENGLISH)
        if (printDebug) Log.w(TAG, "EN tests - current locale is: " + Locale.getDefault())
        performSingleTimeUnitStringComparison(expectedOutputsSingle_EN, printDebug)
        performDualTimeUnitStringComparison(expectedOutputsDual_EN, printDebug)
    }

    // Unit test for durations in the French language
    @Test
    fun time_span_strings_FR(printDebug: Boolean = true) {
        Locale.setDefault(Locale.FRENCH)
        if (printDebug) Log.w(TAG, "FR tests - current locale is: " + Locale.getDefault())
        performSingleTimeUnitStringComparison(expectedOutputsSingle_FR, printDebug)
        performDualTimeUnitStringComparison(expectedOutputsDual_FR, printDebug)
    }

    // Unit test for durations in the Arabic language.
    // Note: Arabic is a RTL language so we get the equivalent of things like:
    // - "minutes 3", or
    // - "hours 23 days 4" etc.

    // Unit test for Arabic - which is a right-to-left language so "1 second" is "<arabic-word-for-second> 1" etc.
    @Test
    fun time_span_strings_AR(printDebug: Boolean = true) {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        if (printDebug) Log.w(TAG, "AR tests - current locale is: " + Locale.getDefault())
        performSingleTimeUnitStringComparison(expectedOutputsSingle_AR, printDebug)
        performDualTimeUnitStringComparison(expectedOutputsDual_AR, printDebug)
    }
}