package org.session.libsignal.utilities

import android.annotation.SuppressLint
import org.session.libsignal.net.Ip

class Snode(val address: String, val port: Int, val publicKeySet: KeySet?, val version: Version) {
    val ip: Ip get() = address.removePrefix("https://").let(::Ip)

    enum class Method(val rawValue: String) {
        GetSwarm("get_snodes_for_pubkey"),
        Retrieve("retrieve"),
        SendMessage("store"),
        DeleteMessage("delete"),
        OxenDaemonRPCCall("oxend_request"),
        Info("info"),
        DeleteAll("delete_all"),
        Batch("batch"),
        Sequence("sequence"),
        Expire("expire"),
        GetExpiries("get_expiries")
    }

    data class KeySet(val ed25519Key: String, val x25519Key: String)

    override fun equals(other: Any?): Boolean {
        return if (other is Snode) {
            address == other.address && port == other.port
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return address.hashCode() xor port.hashCode()
    }

    override fun toString(): String { return "$address:$port" }

    companion object {
        private val CACHE = mutableMapOf<String, Version>()

        @SuppressLint("NotConstructor")
        fun Version(value: String) = CACHE.getOrElse(value) {
            Snode.Version(value)
        }
    }

    @JvmInline
    value class Version(val value: ULong) {
        companion object {
            val ZERO = Version(0UL)
            private const val MASK_BITS = 16
            private const val MASK = 0xFFFFUL

            private fun Sequence<ULong>.foldToVersionAsULong() = take(4).foldIndexed(0UL) { i, acc, it ->
                it and MASK shl (3 - i) * MASK_BITS or acc
            }
        }

        constructor(parts: List<Int>): this(
            parts.asSequence()
                .map { it.toByte().toULong() }
                .foldToVersionAsULong()
        )

        constructor(value: Int): this(value.toULong())

        internal constructor(value: String): this(
            value.splitToSequence(".")
                .map { it.toULongOrNull() ?: 0UL }
                .foldToVersionAsULong()
        )

        operator fun compareTo(other: Version): Int = value.compareTo(other.value)
    }
}
