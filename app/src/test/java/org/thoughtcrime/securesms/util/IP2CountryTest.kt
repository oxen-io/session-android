package org.thoughtcrime.securesms.util

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito.mock

@RunWith(Parameterized::class)
class IP2CountryTest(
    private val ip: String,
    private val country: String
) {
    private val context: Context = mock(Context::class.java)
    private val ip2Country = IP2Country(context, this::class.java.classLoader!!::getResourceAsStream)

    @Test
    fun getCountryNamesCache() {
        assertEquals(country, ip2Country.cacheCountryForIP(ip))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data(): Collection<Array<Any>> = listOf(
            arrayOf("223.121.64.0", "Hong Kong"),
            arrayOf("223.121.64.1", "Hong Kong"),
            arrayOf("223.121.127.0", "Hong Kong"),
            arrayOf("223.121.128.0", "China"),
            arrayOf("223.121.129.0", "China"),
            arrayOf("223.122.0.0", "Hong Kong"),
            arrayOf("223.123.0.0", "Pakistan"),
            arrayOf("223.123.128.0", "China"),
            arrayOf("223.124.0.0", "China"),
            arrayOf("223.128.0.0", "China"),
            arrayOf("223.130.0.0", "Singapore")
        )
    }
}
