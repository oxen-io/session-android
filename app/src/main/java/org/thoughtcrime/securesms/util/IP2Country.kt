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
import java.util.SortedMap
import java.util.TreeMap

class IP2Country private constructor(private val context: Context) {
    private val pathsBuiltEventReceiver: BroadcastReceiver
    val countryNamesCache = mutableMapOf<String, String>()

    private fun Ipv4Int(ip: String): Int {
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

        return result.toInt()
    }

    private val ipv4ToCountry: TreeMap<Int, Int?> by lazy {
        val file = loadFile("geolite2_country_blocks_ipv4.csv")
        CSVReader(FileReader(file.absoluteFile)).use { csv ->
            csv.skip(1)

            csv.asSequence().associateTo(TreeMap()) { cols ->
                Ipv4Int(cols[0]).toInt() to cols[1].toIntOrNull()
            }
        }
    }

    private val countryToNames: Map<Int, String> by lazy {
        val file = loadFile("geolite2_country_locations_english.csv")
        CSVReader(FileReader(file.absoluteFile)).use { csv ->
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
        }
    }

    init {
        populateCacheIfNeeded()
        pathsBuiltEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                populateCacheIfNeeded()
            }
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(pathsBuiltEventReceiver, IntentFilter("pathsBuilt"))
    }

    // TODO: Deinit?
    // endregion

    // region Implementation
    private fun loadFile(fileName: String): File {
        val directory = File(context.applicationInfo.dataDir)
        val file = File(directory, fileName)
        if (directory.list().contains(fileName)) { return file }
        val inputStream = context.assets.open("csv/$fileName")
        val outputStream = FileOutputStream(file)
        val buffer = ByteArray(1024)
        while (true) {
            val count = inputStream.read(buffer)
            if (count < 0) { break }
            outputStream.write(buffer, 0, count)
        }
        inputStream.close()
        outputStream.close()
        return file
    }

    private fun cacheCountryForIP(ip: String): String? {

        // return early if cached
        countryNamesCache[ip]?.let { return it }

        val ipInt = Ipv4Int(ip)
        val bestMatchCountry = ipv4ToCountry.floorEntry(ipInt)?.let { (_, code) ->
            if (code != null) {
                countryToNames[code]
            } else {
                null
            }
        }

        if (bestMatchCountry != null) {
            countryNamesCache[ip] = bestMatchCountry
            return bestMatchCountry
        } else {
            Log.d("Loki","Country name for $ip couldn't be found")
        }
        return null
    }

    private fun populateCacheIfNeeded() {
        ThreadUtils.queue {
            OnionRequestAPI.paths.iterator().forEach { path ->
                path.iterator().forEach { snode ->
                    cacheCountryForIP(snode.ip) // Preload if needed
                }
            }
            Broadcaster(context).broadcast("onionRequestPathCountriesLoaded")
            Log.d("Loki", "Finished preloading onion request path countries.")
        }
    }
    // endregion
}

