package org.thoughtcrime.securesms.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.opencsv.CSVReader
import dagger.hilt.android.qualifiers.ApplicationContext
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.utilities.binarySearchLastAndGet
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.ThreadUtils
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IP2Country @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val pathsBuiltEventReceiver: BroadcastReceiver
    val countryNamesCache = mutableMapOf<String, String?>()

    private fun ipv4Int(ip: String) =
        ip.takeWhile { it != '/' }.split('.')
            .fold(0L) { acc, octet -> acc shl 8 or octet.toLong() }

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
            OnionRequestAPI.paths.asSequence().flatten().map { it.ip }.forEach(::cacheCountryForIP)
            Broadcaster(context).broadcast("onionRequestPathCountriesLoaded")
            Log.d("Loki", "Finished preloading onion request path countries.")
        }
    }

    // TODO: Deinit?

    private fun loadFile(fileName: String): File =
        File(context.applicationInfo.dataDir).let { directory ->
            File(directory, fileName).also { file ->
                if (directory.list()?.contains(fileName) != true) {
                    context.assets.open("csv/$fileName").use { inputStream ->
                        FileOutputStream(file).use(inputStream::copyTo)
                    }
                }
            }
        }

    /**
     * get the country name for the given IP address.
     *
     * Cache the result even if null, as this will improve subsequent searches.
     */
    private fun cacheCountryForIP(ip: String): String? = countryNamesCache.getOrPut(ip) {
        val ipv4 = ipv4Int(ip)
        ipv4ToCountry
            .binarySearchLastAndGet { it.first <= ipv4 }
            ?.second
            ?.let(countryToNames::get)
            .also {
                if (it == null) Log.d("Loki","Country name for $ip couldn't be found")
            }
    }
}
