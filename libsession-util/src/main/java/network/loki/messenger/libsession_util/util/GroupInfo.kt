package network.loki.messenger.libsession_util.util

sealed class GroupInfo {

    data class CommunityGroupInfo(val community: BaseCommunityInfo, val priority: Int) : GroupInfo()

    data class LegacyGroupInfo(
        val sessionId: String,
        val name: String,
        val members: Map<String, Boolean>,
        val hidden: Boolean,
        val encPubKey: ByteArray,
        val encSecKey: ByteArray,
        val priority: Int
    ): GroupInfo() {
        companion object {
            @Suppress("FunctionName")
            external fun NAME_MAX_LENGTH(): Int
        }
    }

}