package org.session.libsignal.crypto

import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.isHex
import java.util.zip.CRC32

/**
 * Based on [mnemonic.js](https://github.com/loki-project/loki-messenger/blob/development/libloki/modules/mnemonic.js) .
 */
class MnemonicCodec(private val loadFileContents: (String) -> String) {

    class Language(private val loadFileContents: (String) -> String, private val configuration: Configuration) {

        data class Configuration(val filename: String, val prefixLength: Int) {

            companion object {
                val english = Configuration("english", 3)
                val japanese = Configuration("japanese", 3)
                val portuguese = Configuration("portuguese", 4)
                val spanish = Configuration("spanish", 4)
            }
        }

        companion object {
            internal val wordSetCache = mutableMapOf<Language, List<String>>()
        }

        internal fun loadWordSet(): List<String> = wordSetCache.getOrPut(this) {
            loadFileContents(configuration.filename).split(",")
        }

        internal fun loadTruncatedWordSet(): List<String> = wordSetCache.getOrPut(this) {
            val prefixLength = configuration.prefixLength
            loadWordSet().map { it.substring(0 until prefixLength) }
        }
    }

    sealed class DecodingError(val description: String) : Exception(description) {
        object Generic : DecodingError("Something went wrong. Please check your mnemonic and try again.")
        object InputTooShort : DecodingError("Looks like you didn't enter enough words. Please check your mnemonic and try again.")
        object InvalidWord : DecodingError("There appears to be an invalid word in your mnemonic. Please check what you entered and try again.")
        object VerificationFailed : DecodingError("Your mnemonic couldn't be verified. Please check what you entered and try again.")
    }

    /**
     * Accepts a [hexEncodedString] and return s a mnemonic.
     */
    fun encode(hexEncodedString: String, languageConfiguration: Language.Configuration = Language.Configuration.english): String {
        var string = hexEncodedString
        val language = Language(loadFileContents, languageConfiguration)
        val wordSet = language.loadWordSet()
        val prefixLength = languageConfiguration.prefixLength

        val n = wordSet.size.toLong()
        val characterCount = string.length
        for (chunkStartIndex in 0..(characterCount - 8) step 8) {
            val chunkEndIndex = chunkStartIndex + 8
            val p1 = string.substring(0 until chunkStartIndex)
            val p2 = swap(string.substring(chunkStartIndex until chunkEndIndex))
            val p3 = string.substring(chunkEndIndex until characterCount)
            string = p1 + p2 + p3
        }

        return string.windowed(8, 8).map {
            val x = it.toLong(16)
            val w1 = x % n
            val w2 = ((x / n) + w1) % n
            val w3 = (((x / n) / n) + w2) % n
            listOf(w1, w2, w3).map(Long::toInt).map { wordSet[it] }
        }.flatten().let {
            val checksumIndex = determineChecksumIndex(it, prefixLength)
            it + it[checksumIndex]
        }.joinToString(" ")
    }

    /**
     * Accepts a [mnemonic] and returns a hexEncodedString
     */
    fun decode(mnemonic: String, languageConfiguration: Language.Configuration = Language.Configuration.english): String {
        val words = mnemonic.split(" ")
        val language = Language(loadFileContents, languageConfiguration)
        val truncatedWordSet = language.loadTruncatedWordSet()
        val prefixLength = languageConfiguration.prefixLength
        val n = truncatedWordSet.size.toLong()

        // Check preconditions
        if (words.size < 13) throw DecodingError.InputTooShort

        fun String.prefix() = substring(0 until prefixLength)

        // Throw on invalid words, as this is the most difficult issue for a user to solve, do this first.
        val wordPrefixes = words
            .onEach { if (it.length < prefixLength) throw DecodingError.InvalidWord }
            .map { it.prefix() }

        val wordIndexes = wordPrefixes.map { truncatedWordSet.indexOf(it) }
            .onEach { if (it < 0) throw DecodingError.InvalidWord }

        // Verify checksum
        val checksumIndex = determineChecksumIndex(words.dropLast(1), prefixLength)
        val expectedChecksumWord = words[checksumIndex]
        if (expectedChecksumWord.prefix() != wordPrefixes.last()) {
            throw DecodingError.VerificationFailed
        }

        // Decode
        return wordIndexes.windowed(3, 3) { (w1, w2, w3) ->
            val x = w1 + n * ((n - w1 + w2) % n) + n * n * ((n - w2 + w3) % n)
            if (x % n != w1.toLong()) throw DecodingError.Generic
            val string = "0000000" + x.toString(16)
            swap(string.substring(string.length - 8 until string.length))
        }.joinToString(separator = "") { it }
    }

    fun sanitizeAndDecodeAsByteArray(mnemonic: String): ByteArray = sanitizeRecoveryPhrase(mnemonic).let(::decode).let(Hex::fromStringCondensed)

    private fun sanitizeRecoveryPhrase(rawMnemonic: String): String = rawMnemonic
        .replace("[^\\w]+".toRegex(), " ") // replace any sequence of non-word characters with a space
        .trim() // remove leading and trailing whitespace (which may have been from prior special chars)
        .split("\\s+".toRegex()) // split on the now properly positioned spaces
        .joinToString(" ") // reassemble

    fun decodeMnemonicOrHexAsByteArray(mnemonicOrHex: String): ByteArray = try {
        // Try to use decode mnemonicOrHex as a mnemonic
        decode(mnemonic = mnemonicOrHex).let(Hex::fromStringCondensed)
    } catch (decodeException: Exception) {
        // It's not a valid mnemonic, if it's pure-hexadecimal then we'll interpret it as a
        // hexadecimal-byte encoded mnemonic... unless it's 66 chars or longer, then it could be
        // an account id.
        mnemonicOrHex.takeIf { it.length < 66 && it.isHex() }
            .runCatching { Hex.fromStringCondensed(this) }
            .getOrNull()
            ?: throw decodeException
    }
}

private fun swap(x: String): String {
    val p1 = x.substring(6 until 8)
    val p2 = x.substring(4 until 6)
    val p3 = x.substring(2 until 4)
    val p4 = x.substring(0 until 2)
    return p1 + p2 + p3 + p4
}

private fun determineChecksumIndex(x: List<String>, prefixLength: Int): Int {
    val bytes = x.joinToString("") { it.substring(0 until prefixLength) }.toByteArray()
    val checksum = CRC32().apply { update(bytes) }.value
    return (checksum % x.size.toLong()).toInt()
}
