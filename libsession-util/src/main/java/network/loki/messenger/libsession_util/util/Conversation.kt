package network.loki.messenger.libsession_util.util

sealed class Conversation {

    abstract var lastRead: Long
    abstract var unread: Boolean

    data class OneToOne(
        val sessionId: String,
        override var lastRead: Long,
        override var unread: Boolean
    ): Conversation()

    data class Community(
        val baseCommunityInfo: BaseCommunityInfo,
        override var lastRead: Long,
        override var unread: Boolean
    ) : Conversation() {
        companion object {
            init {
                System.loadLibrary("session_util")
            }
            external fun parseFullUrl(fullUrl: String): Triple<String, String, ByteArray>?
        }
    }

    data class LegacyGroup(
        val groupId: String,
        override var lastRead: Long,
        override var unread: Boolean
    ): Conversation()
}