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
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader

class IP2Country private constructor(private val context: Context) {
    private val pathsBuiltEventReceiver: BroadcastReceiver
    val countryNamesCache = mutableMapOf<String, String?>()

    private fun ipv4Int(ip: String) =
        ip.takeWhile { it != '/' }.split('.').foldIndexed(0L) { i, acc, s ->
            val asInt = s.toLong()
            acc + (asInt shl (8 * (3 - i)))
        }

    private val ipv4ToCountry by lazy {
        readCsv("geolite2_country_blocks_ipv4.csv")
            .map { cols -> ipv4Int(cols[0]) to cols[1].toIntOrNull() }
    }

    private val countryToNames by lazy {
        readCsv("geolite2_country_locations_english.csv")
            .filter { cols -> !cols[0].isNullOrEmpty() && !cols[1].isNullOrEmpty() }
            .associate { cols -> cols[0].toInt() to cols[5] }
    }

    private fun readCsv(fileName: String) =
        loadFile(fileName)
            .absoluteFile
            .let(::FileReader)
            .let(::CSVReader)
            .apply { skip(1) }
            .readAll()

    init {
        populateCacheIfNeeded()
        pathsBuiltEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                populateCacheIfNeeded()
            }
        }
        LocalBroadcastManager.getInstance(context)
            .registerReceiver(pathsBuiltEventReceiver, IntentFilter("pathsBuilt"))
    }

    private fun populateCacheIfNeeded() {
        ThreadUtils.queue {
            // Preload if needed
            OnionRequestAPI.paths.flatten().map { it.ip }.forEach(::cacheCountryForIP)
            Broadcaster(context).broadcast("onionRequestPathCountriesLoaded")
            Log.d("Loki", "Finished preloading onion request path countries.")
        }
    }

    // TODO: Deinit?

    private fun loadFile(fileName: String): File {
        val directory = File(context.applicationInfo.dataDir)
        return File(directory, fileName).also { file ->
            if (directory.list()?.contains(fileName) != true) {
                context.assets.open("csv/$fileName").use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }
    }

    /**
     * get the country name for the given IP address.
     *
     * Cache the result even if null, as this will improve subsequent searches.
     */
    private fun cacheCountryForIP(ip: String): String? =
        countryNamesCache.getOrPut(ip) {
            val ipv4 = ipv4Int(ip)

            ipv4ToCountry
                .binarySearchLast { it.first <= ipv4 }
                ?.second
                ?.let(countryToNames::get)
                .also {
                    if (it == null) Log.d("Loki","Country name for $ip couldn't be found")
                }
        }

    companion object {
        lateinit var shared: IP2Country

        val isInitialized: Boolean get() = Companion::shared.isInitialized

        fun configureIfNeeded(context: Context) {
            if (isInitialized) return
            shared = IP2Country(context.applicationContext)
        }
    }
}

inline fun <T> List<T>.binarySearchLast(predicate: (T) -> Boolean): T? {
    var low = 0
    var high = size - 1
    var result: T? = null

    while (low <= high) {
        val mid = (low + high) / 2

        this[mid].takeIf(predicate)?.let {
            result = it
            low = mid + 1
        } ?: run {
            high = mid - 1
        }
    }

    return result
}
