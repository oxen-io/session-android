package network.loki.messenger.libsession_util.util

import org.session.libsignal.utilities.Hex

sealed class Conversation {

    abstract var lastRead: Long
    abstract var unread: Boolean

    data class OneToOne(
        val sessionId: String,
        override var lastRead: Long,
        override var unread: Boolean
    ): Conversation()

    data class Community(
        val baseUrl: String,
        val room: String, // lowercase
        val pubKey: ByteArray,
        override var lastRead: Long,
        override var unread: Boolean
    ) : Conversation() {

        val pubKeyHex: String
            get() = Hex.toStringCondensed(pubKey)
        companion object {
            init {
                System.loadLibrary("session_util")
            }
            external fun parseFullUrl(fullUrl: String): Triple<String, String, ByteArray>?
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Community

            if (baseUrl != other.baseUrl) return false
            if (room != other.room) return false
            if (!pubKey.contentEquals(other.pubKey)) return false
            if (lastRead != other.lastRead) return false

            return true
        }

        override fun hashCode(): Int {
            var result = baseUrl.hashCode()
            result = 31 * result + room.hashCode()
            result = 31 * result + pubKey.contentHashCode()
            result = 31 * result + lastRead.hashCode()
            return result
        }
    }

    data class LegacyGroup(
        val groupId: String,
        override var lastRead: Long,
        override var unread: Boolean
    ): Conversation()
}