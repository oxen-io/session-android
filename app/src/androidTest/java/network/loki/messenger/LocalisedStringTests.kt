package network.loki.messenger

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import junit.framework.TestCase.assertEquals
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.bytebuddy.asm.Advice.Local
import org.junit.Test
import org.junit.runner.RunWith
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.util.LocalisedTimeUtil
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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

    private val allDurations    = listOf(oneSecond, twoSeconds, oneMinute, twoMinutes, oneHour, twoHours, oneDay, twoDays, oneDaySevenHours, fourDaysTwentyThreeHours, oneWeekTwoDays, twoWeekTwoDays)
    private val allDescriptions = listOf("1s", "2s", "1m", "2m", "1h", "2h", "1d", "2d", "1d7h", "4d23h", "1w2d", "2w2d")

    // Expected outputs for English
    private val expectedOutputsSingle_EN = listOf("1 second", "2 seconds", "1 minute", "2 minutes", "1 hour", "2 hours",
                                                  "1 day", "2 days", "1 day", "4 days", "1 week", "2 weeks")

    private val expectedOutputsDual_EN = listOf("0 minutes 1 second", "0 minutes 2 seconds", "1 minute 0 seconds",
                                                "2 minutes 0 seconds", "1 hour 0 minutes", "2 hours 0 minutes",
                                                "1 day 0 hours", "2 days 0 hours", "1 day 7 hours", "4 days 23 hours",
                                                "1 week 2 days", "2 weeks 2 days")

    private val expectedOutputsSingle_FR = listOf("1 seconde", "2 secondes", "1 minute", "2 minutes", "1 heure", "2 heures",
                                                  "1 jour", "2 jours", "1 jour", "4 jours", "1 semaine", "2 semaines")

    private val expectedOutputsDual_FR = listOf("0 minutes 1 seconde", "0 minutes 2 secondes", "1 minute 0 secondes",
                                                "2 minutes 0 secondes", "1 heure 0 minutes", "2 heures 0 minutes",
                                                "1 jour 0 heures", "2 jours 0 heures", "1 jour 7 heures", "4 jours 23 heures",
                                                "1 semaine 2 jours", "2 semaines 2 jours")

    private val expectedOutputsSingle_AR =   listOf("1 ثانية",  // 1 second
                                                    "2 ثانية",  // 2 seconds
                                                    "1 دقيقة",  // 1 minute
                                                    "2 دقائق",  // 2 minutes
                                                    "1 ساعة",   // 1 hour
                                                    "2 ساعات",  // 2 hours
                                                    "1 يوم",   // 1 day
                                                    "2 أيام",   // 2 days
                                                    "1 يوم",    // 1 day 7 hours as single unit (1 day)
                                                    "4 أيام",   // 4 days 23 hours as single unit (4 days)
                                                    "1 أسبوع",  // 1 week 2 days as single unit (1 week)
                                                    "2 أسابيع") // 2 weeks 2 days as single unit (2 weeks)

    //                                                          // Note: As Arabic is a Right-to-Left language the larger unit goes on the right!
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

//    private val expectedOutputsDual_AR = listOf("0 minutes 1 seconde", "0 minutes 2 secondes", "1 minute 0 secondes",
//        "2 minutes 0 secondes", "1 heure 0 minutes", "2 heures 0 minutes",
//        "1 jour 0 heures", "2 jours 0 heures", "1 jour 7 heures", "4 jours 23 heures",
//        "1 semaine 2 jours", "2 semaines 2 jours")

    val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ApplicationContext


    // The map containing key->value pairs such as "Minutes" -> "minutos" and "Hours" -> "horas" for Spanish etc.
    //var timeUnitStringsMap: Map<String, String> = loadTimeStringMap(context, Locale.ENGLISH)



    // Method to load the time string map for a given locale
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

    // ACL Note: This `to2partString` method is from Andy and will print things like "2h 14m" but it does NOT localise at all based on locale!
    /*
    private fun Duration.to2partString(): String? =
        toComponents { days, hours, minutes, seconds, nanoseconds -> listOf(days.days, hours.hours, minutes.minutes, seconds.seconds) }
            .filter { it.inWholeSeconds > 0L }.take(2).takeIf { it.isNotEmpty() }?.joinToString(" ")
    */





    @Test
    fun time_span_strings_EN() {
        // Note: For locales from language codes:
        // - Arabic is "ar"
        // - Japanese is "ja"
        // - Urdu is "ur"

        val l = Locale.ENGLISH // English is not RTL so we don't have to check large/small order
        Locale.setDefault(l)
        //LocalisedTimeUtil.timeUnitStringsMap = loadTimeStringMap(context, l)

        val printDebug = true
        val lastIndex = allDurations.count() - 1
        for (i in 0..lastIndex) {
            var large = LocalisedTimeUtil.getDurationWithLargestTimeUnit(context, allDurations[i])
            if (printDebug) println("Single time unit ${allDescriptions[i]} - $large")
            assertEquals(expectedOutputsSingle_EN[i], large)
        }

        for (i in 0..lastIndex) {
            var result = LocalisedTimeUtil.getDurationWithLargestTwoTimeUnits(context, allDurations[i])
            if (printDebug) println("Dual time units ${allDescriptions[i]} - $result")
            assertEquals(expectedOutputsDual_EN[i], result)
        }
    }

    // Unit test for French
    @Test
    fun time_span_strings_FR() {
        val l = Locale.FRENCH
        Locale.setDefault(l)
        LocalisedTimeUtil.loadTimeStringMap(context, l)

        val printDebug = true
        val lastIndex = allDurations.count() - 1
        for (i in 0..lastIndex) {
            var large = LocalisedTimeUtil.getDurationWithLargestTimeUnit(context, allDurations[i])
            if (printDebug) println("Single time unit ${allDescriptions[i]} - $large")
            assertEquals(expectedOutputsSingle_FR[i], large)
        }

        for (i in 0..lastIndex) {
            var result =  LocalisedTimeUtil.getDurationWithLargestTwoTimeUnits(context, allDurations[i])
            if (printDebug) println("Dual time units ${allDescriptions[i]} - $result")
            assertEquals(expectedOutputsDual_FR[i], result)
        }
    }

    // Unit test for Arabic - which is a right-to-left language so "1 second" is "<arabic-word-for-second> 1" etc.
    @Test
    fun time_span_strings_AR() {
        val l = Locale.forLanguageTag("ar")
        Locale.setDefault(l)
        LocalisedTimeUtil.loadTimeStringMap(context, l)

        val printDebug = true
        val lastIndex = allDurations.count() - 1
        for (i in 0..lastIndex) {
            var large = LocalisedTimeUtil.getDurationWithLargestTwoTimeUnits(context, allDurations[i])
            if (printDebug) println("Single time unit ${allDescriptions[i]} - $large")
            assertEquals(expectedOutputsSingle_AR[i], large)
        }

        for (i in 0..lastIndex) {
            var result = LocalisedTimeUtil.getDurationWithLargestTwoTimeUnits(context, allDurations[i])
            if (printDebug) println("Dual time units ${allDescriptions[i]} - $result")
            assertEquals(expectedOutputsDual_AR[i], result)
        }
    }
}