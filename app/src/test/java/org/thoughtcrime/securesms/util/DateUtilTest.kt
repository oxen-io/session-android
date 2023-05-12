package org.thoughtcrime.securesms.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDateFormat
import org.robolectric.shadows.ShadowSettings
import java.util.Locale
import java.util.TimeZone
import java.util.Calendar
import java.util.Calendar.JANUARY
import java.util.Calendar.NOVEMBER
import java.util.Calendar.DECEMBER

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = TestApplication::class, shadows = [ShadowDateFormat::class])
class DateUtilTest(
    private val expected: String,
    private val locale: Locale,
    private val then: Long,
    private val now: Long,
    private val is24HourTime: Boolean
) {
    private val clock = Mockito.mock(Clock::class.java)
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val dateUtil = DateUtil(context, clock)

    @Test
    fun test() {
        whenever(clock.currentTimeMillis).thenReturn(now)
        ShadowSettings.set24HourTimeFormat(is24HourTime)
        val timestamp = dateUtil.getDisplayFormattedTimeSpanString(then, locale)
        assertEquals(expected, timestamp)
    }

    companion object {
        private val zone = TimeZone.getTimeZone("GMT")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: myMethod({0}, {1})")
        fun parameters(): List<Array<Any>> = listOf(
            timespan(
                millis(2001, JANUARY),
                millis(2001, JANUARY),
                assertion(Locale.US, "Now", "Now"),
                assertion(Locale.CHINA, "Now", "Now"),
                assertion(Locale.ENGLISH, "Now", "Now")
            ),
            timespan(
                millis(2001, JANUARY),
                millis(2001, JANUARY),
                assertion(Locale.US, "Now", "Now"),
            ),
            timespan(
                millis(2001, DECEMBER, 30),
                millis( 2001, DECEMBER, 30, second = 59),
                assertion(Locale.US, "Now", "Now"),
            ),
            timespan(
                millis(2001, DECEMBER, 30),
                millis( 2001, DECEMBER, 30, minute = 1),
                assertion(Locale.US, "Now", "Now")
            ),
            timespan(
                millis(2001, DECEMBER, 30),
                millis( 2001, DECEMBER, 30, minute = 1, second = 1),
                assertion(Locale.US, "10:30 AM", "10:30"),
                assertion(Locale.JAPAN, "10:30 AM", "10:30")
            ),
            timespan(
                millis(2001, DECEMBER, 30),
                millis( 2001, DECEMBER, 30, minute = 1, second = 1),
                assertion(Locale.US, "10:30 AM", "10:30")
            ),
            timespan(
                millis(2001, DECEMBER, 30, hour = 12, minute = 15),
                millis( 2001, DECEMBER, 30, hour = 12, minute = 16, second = 1),
                        assertion(Locale.US, "10:45 PM", "22:45")
            ),
            timespan(
                millis(2001, DECEMBER, 25),
                millis( 2001, DECEMBER, 30),
                assertion(Locale.US, "Tue 10:30 AM", "Tue 10:30")
            ),
            timespan(
                millis(2001, NOVEMBER, 25),
                millis( 2001, DECEMBER, 10),
                assertion(Locale.US, "Nov 25 10:30 AM", "Nov 25 10:30")
            ),
            timespan(
                millis(2001, JANUARY),
                millis( 2003, JANUARY),
                assertion(Locale.US, "Jan 1 10:30 AM, 2001", "Jan 1 10:30, 2001")
            ),
        ).flatten()

        private fun assertion(
            locale: Locale,
            expected: String,
            expected24HourTime: String
        ): Timespan.() -> List<Array<Any>> = {
            listOf(
                assertion(locale, expected, false),
                assertion(locale, expected24HourTime, true)
            )
        }

        private fun timespan(
            start: Locale.() -> Long,
            end: Locale.() -> Long,
            vararg assertions: Timespan.() -> List<Array<Any>>
        ): List<Array<Any>> = Timespan(start, end).run { assertions.map { it() } }.flatten()

        private fun millis(
            year: Int,
            month: Int = 0,
            date: Int = 1,
            hour: Int = 0,
            minute: Int = 0,
            second: Int = 0
        ): (Locale) -> Long = {
            Calendar.getInstance(zone, it)
                .apply { set(year, month, date, hour, minute, second) }
                .timeInMillis
        }

        class Timespan(
            val start: (Locale) -> Long,
            val end: (Locale) -> Long
        ) {
            fun assertion(
                locale: Locale,
                expected: String,
                is24HourTime: Boolean
            ): Array<Any> = arrayOf(expected, locale, start(locale), end(locale), is24HourTime)
        }
    }
}

class TestApplication : Application()
