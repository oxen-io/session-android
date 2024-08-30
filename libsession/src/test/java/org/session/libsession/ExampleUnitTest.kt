package org.session.libsession

import android.text.format.DateUtils
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)

        /*
        val s = getLocalizedTodayString()
        println(s)

        val now = Calendar.getInstance()
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val s2 = DateUtils.getRelativeTimeSpanString(
            startOfDay.timeInMillis,
            now.timeInMillis,
            DateUtils.DAY_IN_MILLIS,
            DateUtils.FORMAT_SHOW_DATE
        ).toString();
        println(s2)
        */
    }
}