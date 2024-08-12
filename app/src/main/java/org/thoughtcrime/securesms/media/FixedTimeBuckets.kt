package org.thoughtcrime.securesms.media

import androidx.annotation.StringRes
import network.loki.messenger.R
import java.time.ZonedDateTime
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * A data structure that describes a series of time points in the past. It's primarily
 * used to bucket items into categories like "Today", "Yesterday", "This week", "This month", etc.
 *
 * Call [getBucketText] to get the appropriate string resource for a given time. If no bucket is
 * appropriate, it will return null.
 */
class FixedTimeBuckets(
    private val startOfToday: ZonedDateTime,
    private val startOfYesterday: ZonedDateTime,
    private val startOfThisWeek: ZonedDateTime,
    private val startOfThisMonth: ZonedDateTime
) {
    constructor(now: ZonedDateTime = ZonedDateTime.now()) : this(
        startOfToday = now.toLocalDate().atStartOfDay(now.zone),
        startOfYesterday = now.toLocalDate().minusDays(1).atStartOfDay(now.zone),
        startOfThisWeek = now.toLocalDate()
            .with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)
            .atStartOfDay(now.zone),
        startOfThisMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay(now.zone)
    )

    /**
     * Test the given time against the buckets and return the appropriate string resource the time
     * bucket. If no bucket is appropriate, it will return null.
     */
    @StringRes
    fun getBucketText(time: ZonedDateTime): Int? {
        return when {
            time >= startOfToday -> R.string.BucketedThreadMedia_Today
            time >= startOfYesterday -> R.string.BucketedThreadMedia_Yesterday
            time >= startOfThisWeek -> R.string.BucketedThreadMedia_This_week
            time >= startOfThisMonth -> R.string.BucketedThreadMedia_This_month
            else -> null
        }
    }
}