package org.session.libsession.utilities.bencode

import java.util.LinkedList

object Bencode {
    class Decoder(source: ByteArray) {

        private val iterator = LinkedList<Byte>().apply {
            addAll(source.asIterable())
        }

        /**
         * Decode an element based on next marker assumed to be string/int/list/dict or return null
         */
        fun decode(): BencodeElement? {
            val result = when (iterator.peek()?.toInt()?.toChar()) {
                in NUMBERS -> decodeString()
                INT_INDICATOR -> decodeInt()
                LIST_INDICATOR -> decodeList()
                DICT_INDICATOR -> decodeDict()
                else -> {
                    null
                }
            }
            return result
        }

        /**
         * Decode a string element from iterator assumed to have structure `{length}:{data}`
         */
        private fun decodeString(): BencodeString? {
            val lengthStrings = buildString {
                while (iterator.isNotEmpty() && iterator.peek()?.toInt()?.toChar() != SEPARATOR) {
                    append(iterator.pop().toInt().toChar())
                }
            }
            iterator.pop() // drop `:`
            val length = lengthStrings.toIntOrNull(10) ?: return null
            val remaining = (0 until length).map { iterator.pop() }.toByteArray()
            return BencodeString(remaining)
        }

        /**
         * Decode an int element from iterator assumed to have structure `i{int}e`
         */
        private fun decodeInt(): BencodeElement? {
            iterator.pop() // drop `i`
            val intString = buildString {
                while (iterator.isNotEmpty() && iterator.peek()?.toInt()?.toChar() != END_INDICATOR) {
                    append(iterator.pop().toInt().toChar())
                }
            }
            val asInt = intString.toIntOrNull(10) ?: return null
            iterator.pop() // drop `e`
            return BencodeInteger(asInt)
        }

        /**
         * Decode a list element from iterator assumed to have structure `l{data}e`
         */
        private fun decodeList(): BencodeElement {
            iterator.pop() // drop `l`
            val listElements = mutableListOf<BencodeElement>()
            while (iterator.isNotEmpty() && iterator.peek()?.toInt()?.toChar() != END_INDICATOR) {
                decode()?.let { nextElement ->
                    listElements += nextElement
                }
            }
            iterator.pop() // drop `e`
            return BencodeList(listElements)
        }

        /**
         * Decode a dict element from iterator assumed to have structure `d{data}e`
         */
        private fun decodeDict(): BencodeElement? {
            iterator.pop() // drop `d`
            val dictElements = mutableMapOf<String,BencodeElement>()
            while (iterator.isNotEmpty() && iterator.peek()?.toInt()?.toChar() != END_INDICATOR) {
                val key = decodeString() ?: return null
                val value = decode() ?: return null
                dictElements += key.value.decodeToString() to value
            }
            iterator.pop() // drop `e`
            return BencodeDict(dictElements)
        }

        companion object {
            private val NUMBERS = arrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
            private const val INT_INDICATOR = 'i'
            private const val LIST_INDICATOR = 'l'
            private const val DICT_INDICATOR = 'd'
            private const val END_INDICATOR = 'e'
            private const val SEPARATOR = ':'
        }

    }

}

sealed class BencodeElement {
    abstract fun encode(): ByteArray
}

fun String.bencode() = BencodeString(this.encodeToByteArray())
fun Int.bencode() = BencodeInteger(this)

data class BencodeString(val value: ByteArray): BencodeElement() {
    override fun encode(): ByteArray = buildString {
        append(value.size.toString())
        append(':')
    }.toByteArray() + value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BencodeString

        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}
data class BencodeInteger(val value: Int): BencodeElement() {
    override fun encode(): ByteArray = buildString {
        append('i')
        append(value.toString())
        append('e')
    }.toByteArray()
}
data class BencodeList(val values: List<BencodeElement>): BencodeElement() {

    constructor(vararg values: BencodeElement) : this(values.toList())

    override fun encode(): ByteArray = "l".toByteArray() +
            values.fold(byteArrayOf()) { array, element -> array + element.encode() } +
            "e".toByteArray()
}
data class BencodeDict(val values: Map<String, BencodeElement>): BencodeElement() {

    constructor(vararg values: Pair<String, BencodeElement>) : this(values.toMap())

    override fun encode(): ByteArray = "d".toByteArray() +
            values.entries.fold(byteArrayOf()) { array, (key, value) ->
                array + key.bencode().encode() + value.encode()
            } + "e".toByteArray()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BencodeDict

        if (values != other.values) return false

        return true
    }

    override fun hashCode(): Int {
        return values.hashCode()
    }


}