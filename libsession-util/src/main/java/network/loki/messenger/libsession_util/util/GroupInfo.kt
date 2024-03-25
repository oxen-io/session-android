package network.loki.messenger.libsession_util.util

sealed class GroupInfo {

    data class CommunityGroupInfo(val community: BaseCommunityInfo, val priority: Int) : GroupInfo()

    data class LegacyGroupInfo(
        val sessionId: String,
        val name: String,
        val members: Map<String, Boolean>,
        val encPubKey: ByteArray,
        val encSecKey: ByteArray,
        val priority: Int,
        val disappearingTimer: Long,
        val joinedAt: Long
    ): GroupInfo() {
        companion object {
            @Suppress("FunctionName")
            external fun NAME_MAX_LENGTH(): Int
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as LegacyGroupInfo

            if (sessionId != other.sessionId) return false
            if (name != other.name) return false
            if (members != other.members) return false
            if (!encPubKey.contentEquals(other.encPubKey)) return false
            if (!encSecKey.contentEquals(other.encSecKey)) return false
            if (priority != other.priority) return false
            if (disappearingTimer != other.disappearingTimer) return false
            if (joinedAt != other.joinedAt) return false

            return true
        }

        override fun hashCode(): Int {
            var result = sessionId.hashCode()
            result = 31 * result + name.hashCode()
            result = 31 * result + members.hashCode()
            result = 31 * result + encPubKey.contentHashCode()
            result = 31 * result + encSecKey.contentHashCode()
            result = 31 * result + priority
            result = 31 * result + disappearingTimer.hashCode()
            result = 31 * result + joinedAt.hashCode()
            return result
        }
    }

}