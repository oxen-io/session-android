package network.loki.messenger.libsession_util.util

import org.session.libsignal.utilities.SessionId

sealed class GroupInfo {

    data class CommunityGroupInfo(val community: BaseCommunityInfo, val priority: Long) : GroupInfo()

    data class ClosedGroupInfo(
        val groupSessionId: SessionId,
        val adminKey: ByteArray,
        val authData: ByteArray,
        val priority: Long,
        val invited: Boolean,
    ): GroupInfo() {

        val kicked: Boolean
            get() = adminKey.isEmpty() && authData.isEmpty()

        fun setKicked(): ClosedGroupInfo {
            if (kicked) {
                return this
            }

            return copy(
                adminKey = ByteArray(0),
                authData = ByteArray(0),
            )
        }

        companion object {
            private const val AUTH_DATA_LENGTH = 100
            fun isAuthData(byteArray: ByteArray) = byteArray.size == AUTH_DATA_LENGTH
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ClosedGroupInfo

            if (groupSessionId != other.groupSessionId) return false
            if (!adminKey.contentEquals(other.adminKey)) return false
            return authData.contentEquals(other.authData)
        }

        override fun hashCode(): Int {
            var result = groupSessionId.hashCode()
            result = 31 * result + adminKey.contentHashCode()
            result = 31 * result + authData.contentHashCode()
            return result
        }

        fun signingKey(): ByteArray {
            return if (adminKey.isNotEmpty()) adminKey else authData
        }

        fun hasAdminKey() = adminKey.isNotEmpty()

    }

    data class LegacyGroupInfo(
        val sessionId: SessionId,
        val name: String,
        val members: Map<String, Boolean>,
        val encPubKey: ByteArray,
        val encSecKey: ByteArray,
        val priority: Long,
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
            result = 31 * result + priority.hashCode()
            result = 31 * result + disappearingTimer.hashCode()
            result = 31 * result + joinedAt.hashCode()
            return result
        }

    }

}