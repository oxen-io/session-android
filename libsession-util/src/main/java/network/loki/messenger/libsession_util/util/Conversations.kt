package network.loki.messenger.libsession_util.util

sealed class Conversation {
    data class OneToOne(
        val sessionId: String,
        val lastRead: Long,
        val expiryMode: ExpiryMode,
    ): Conversation()

    data class OpenGroup(
        val baseUrl: String,
        val room: String,
        val pubKey: ByteArray,
        val lastRead: Long,
    ) : Conversation() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as OpenGroup

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

    data class LegacyClosedGroup(
        val groupId: String,
        val lastRead: Long,
        val expiryMode: ExpiryMode
    ): Conversation()
}