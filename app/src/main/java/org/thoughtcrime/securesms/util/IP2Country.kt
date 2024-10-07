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
import java.util.TreeMap

private fun ipv4Int(ip: String): UInt {
    var result = 0L
    var currentValue = 0L
    var octetIndex = 0

    for (char in ip) {
        if (char == '.' || char == '/') {
            result = result or (currentValue shl (8 * (3 - octetIndex)))
            currentValue = 0
            octetIndex++
            if (char == '/') break
        } else {
            currentValue = currentValue * 10 + (char - '0')
        }
    }

    // Handle the last octet
    result = result or (currentValue shl (8 * (3 - octetIndex)))

    return result.toUInt()
}

class IP2Country internal constructor(
    private val context: Context,
    private val openStream: (String) -> InputStream = context.assets::open
) {
    val countryNamesCache = mutableMapOf<String, String>()

    private val ipv4ToCountry by lazy {
        openStream("geolite2_country_blocks_ipv4.bin")
            .let(::DataInputStream)
            .use {
                TreeMap<UInt, Int>().apply {
                    while (it.available() > 0) {
                        val ip = it.readInt().toUInt()
                        val code = it.readInt()
                        put(ip, code)
                    }
                }
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

        public lateinit var shared: IP2Country

        public val isInitialized: Boolean get() = Companion::shared.isInitialized

        public fun configureIfNeeded(context: Context) {
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
        val bestMatchCountry = ipv4ToCountry.floorEntry(ipInt)?.value?.let { countryToNames[it] }

        if (bestMatchCountry != null) countryNamesCache[ip] = bestMatchCountry
        else Log.d("Loki","Country name for $ip couldn't be found")

        return bestMatchCountry
    }

    private fun populateCacheIfNeeded() {
        ThreadUtils.queue {
            OnionRequestAPI.paths.iterator().forEach { path ->
                path.iterator().forEach { snode ->
                    cacheCountryForIP(snode.ip) // Preload if needed
                }
            }
            Broadcaster(context).broadcast("onionRequestPathCountriesLoaded")
        }
    }
    // endregion
}
