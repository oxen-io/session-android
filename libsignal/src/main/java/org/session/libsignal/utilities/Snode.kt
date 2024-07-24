package org.session.libsignal.utilities

import org.thoughtcrime.securesms.util.Ip

class Snode(val address: String, val port: Int, val publicKeySet: KeySet?) {
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
}
