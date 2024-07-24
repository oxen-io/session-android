package org.thoughtcrime.securesms.util

import android.content.Context
import com.opencsv.CSVReader
import dagger.hilt.android.qualifiers.ApplicationContext
import org.session.libsession.utilities.binarySearchLastAndGet
import org.session.libsession.utilities.computeIfAbsentV23
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.associateByNotNull
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import javax.inject.Inject
import javax.inject.Singleton

private data class IpAndCountryId(val ip: Ip, val countryId: Int?)

/**
 * A class that provides functionality to retrieve country names for supplied IP addresses.
 *
 * This class uses CSV files to map IP addresses to country names. It maintains an in-memory cache
 * to optimize repeated lookups. The class retrieves IP-to-country mappings from a CSV file and uses
 * another CSV file to map country IDs to country names.
 *
 * @param context The application context used to access assets and read CSV files.
 */
@Singleton
class IpToCountryName @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Retrieves the country name for the given IP address.
     *
     * This operator function fetches and caches the country information associated with the specified IP address.
     *
     * @param ip The IP address for which to retrieve the country information.
     * @return The country name associated with the given IP address or null if unavailable.
     */
    operator fun get(ip: Ip) = getAndCacheCountryForIP(ip)

    private val countryNamesCache = mutableMapOf<Ip, String?>()
    private val ipAndCountryIds: List<IpAndCountryId> by lazy {
        context.readCsv(IpToCountry.FILE_NAME)
            .mapNotNull(::parseIpAndCountryId)
            .toList()
    }

    private object IpToCountry {
        const val FILE_NAME = "geolite2_country_blocks_ipv4.csv"
        const val IP = "network"
        const val COUNTRY_ID = "registered_country_geoname_id"
    }

    /**
     * Parse the given [row] of the csv into an [IpAndCountryId].
     */
    private fun parseIpAndCountryId(row: Map<String, String>): IpAndCountryId? = IpToCountry.run {
        IpAndCountryId(
            ip = Ip(row[IP] ?: return null),
            countryId = row[COUNTRY_ID]?.toIntOrNull() ?: return null
        )
    }

    private object CountryToNames {
        const val FILE_NAME = "geolite2_country_locations_english.csv"
        const val COUNTRY_ID = "geoname_id"
        const val LOCALE_CODE = "locale_code"
        const val COUNTRY_NAME = "country_name"
    }

    private val countryIdToNames: Map<Int, String> by lazy {
        CountryToNames.run {
            context.readCsv(FILE_NAME)
                .filter { it[LOCALE_CODE]?.isNotEmpty() == true }
                .associateByNotNull(keySelector = { it[COUNTRY_ID]?.toIntOrNull() }, valueTransform = { it[COUNTRY_NAME] })
        }
    }

    /**
     * Get the country name for the given IP address.
     *
     * This function will cache the result even if null, as this will improve subsequent searches.
     */
    private fun getAndCacheCountryForIP(ip: Ip): String? = countryNamesCache.computeIfAbsentV23(ip) {
        ipAndCountryIds.binarySearchLastAndGet { it.ip <= ip }
            ?.let { countryIdToNames[it.countryId] }
            .also {
                if (it == null) Log.d("Loki","Country name for $ip couldn't be found")
            }
    }
}

/**
 * Reads a CSV file from the application's data directory and returns its content as a sequence of maps.
 *
 * The keys of the maps are taken from the first row of the CSV file, and the values are taken from the remaining rows.
 * If the CSV file is empty, an empty sequence is returned.
 *
 * @receiver Context The context used to load the file.
 * @param fileName The name of the CSV file to be read.
 * @return A sequence of maps representing the rows of the CSV file.
 */
private fun Context.readCsv(fileName: String): Sequence<Map<String, String>> =
    loadFile(fileName)
        .absoluteFile
        .let(::FileReader)
        .let(::CSVReader)
        .iterator()
        .run {
            when {
                // get keys from the first row, get the values from the remaining rows if any
                hasNext() -> next().let { keys -> asSequence().map { keys.zip(it).toMap() } }
                else -> emptySequence()
            }
        }

/**
 * Loads a file from the assets folder into the application's data directory.
 *
 * If the file already exists in the data directory, it will not be overwritten.
 * If the file does not exist, it will be copied from the assets folder.
 *
 * @receiver Context The context used to access the assets and application info.
 * @param fileName The name of the file to be loaded.
 * @return The loaded [File] object.
 */
private fun Context.loadFile(fileName: String): File =
    File(applicationInfo.dataDir).let { directory ->
        File(directory, fileName).also { file ->
            if (directory.list()?.contains(fileName) != true) {
                assets.open("csv/$fileName").use { FileOutputStream(file).use(it::copyTo) }
            }
        }
    }
