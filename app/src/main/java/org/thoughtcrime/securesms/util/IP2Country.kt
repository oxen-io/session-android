package org.thoughtcrime.securesms.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.opencsv.CSVReader
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.ThreadUtils
import java.io.DataInputStream
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.absoluteValue

private fun ipv4Int(ip: String): UInt =
    ip.split(".", "/", ",").take(4).fold(0U) { acc, s -> acc shl 8 or s.toUInt() }

@OptIn(ExperimentalUnsignedTypes::class)
class IP2Country internal constructor(
    private val context: Context,
    private val openStream: (String) -> InputStream = context.assets::open
) {
    val countryNamesCache = mutableMapOf<String, String>()

    private val ips: UIntArray by lazy { ipv4ToCountry.first }
    private val codes: IntArray by lazy { ipv4ToCountry.second }

    private val ipv4ToCountry: Pair<UIntArray, IntArray> by lazy {
        openStream("geolite2_country_blocks_ipv4.bin")
            .let(::DataInputStream)
            .use {
                val size = it.available() / 8

                val ips = UIntArray(size)
                val codes = IntArray(size)
                var i = 0

                while (it.available() > 0) {
                    ips[i] = it.readInt().toUInt()
                    codes[i] = it.readInt()
                    i++
                }

                ips to codes
            }
    }

    private val countryToNames: Map<Int, String> by lazy {
        CSVReader(InputStreamReader(openStream("csv/geolite2_country_locations_english.csv"))).use { csv ->
            csv.skip(1)

            csv.asSequence()
                .filter { cols -> !cols[0].isNullOrEmpty() && !cols[1].isNullOrEmpty() }
                .associate { cols ->
                    cols[0].toInt() to cols[5]
                }
        }
    }

    // region Initialization
    companion object {

        lateinit var shared: IP2Country

        val isInitialized: Boolean get() = Companion::shared.isInitialized

        fun configureIfNeeded(context: Context) {
            if (isInitialized) { return; }
            shared = IP2Country(context.applicationContext)

            val pathsBuiltEventReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    shared.populateCacheIfNeeded()
                }
            }
            LocalBroadcastManager.getInstance(context).registerReceiver(pathsBuiltEventReceiver, IntentFilter("pathsBuilt"))
        }
    }

    init {
        populateCacheIfNeeded()
    }

    // TODO: Deinit?
    // endregion

    // region Implementation
    internal fun cacheCountryForIP(ip: String): String? {
        // return early if cached
        countryNamesCache[ip]?.let { return it }

        val ipInt = ipv4Int(ip)
        val index = ips.binarySearch(ipInt).let { it.takeIf { it >= 0 } ?: (it.absoluteValue - 2) }
        val code = codes.getOrNull(index)
        val bestMatchCountry = countryToNames[code]

        if (bestMatchCountry != null) countryNamesCache[ip] = bestMatchCountry
        else Log.d("Loki","Country name for $ip couldn't be found")

        return bestMatchCountry
    }

    private fun populateCacheIfNeeded() {
        ThreadUtils.queue {
            val start = System.currentTimeMillis()
            OnionRequestAPI.paths.iterator().forEach { path ->
                path.iterator().forEach { snode ->
                    cacheCountryForIP(snode.ip) // Preload if needed
                }
            }
            Log.d("Loki","Cache populated in ${System.currentTimeMillis() - start}ms")
            Broadcaster(context).broadcast("onionRequestPathCountriesLoaded")
        }
    }
    // endregion
}
