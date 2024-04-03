package org.session.libsession.utilities

import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.utilities.SessionId
import org.session.libsignal.messages.SignalServiceGroup
import org.session.libsignal.utilities.Hex
import java.io.IOException

object GroupUtil {
    const val CLOSED_GROUP_PREFIX = "__textsecure_group__!"
    const val OPEN_GROUP_PREFIX = "__loki_public_chat_group__!"
    const val OPEN_GROUP_INBOX_PREFIX = "__open_group_inbox__!"

    @JvmStatic
    fun getEncodedOpenGroupID(groupID: ByteArray): String {
        return OPEN_GROUP_PREFIX + Hex.toStringCondensed(groupID)
    }

    @JvmStatic
    fun getEncodedOpenGroupInboxID(openGroup: OpenGroup, sessionId: SessionId): Address {
        val openGroupInboxId =
            "${openGroup.server}!${openGroup.publicKey}!${sessionId.hexString}".toByteArray()
        return getEncodedOpenGroupInboxID(openGroupInboxId)
    }

    @JvmStatic
    fun getEncodedOpenGroupInboxID(groupInboxID: ByteArray): Address {
        return Address.fromSerialized(OPEN_GROUP_INBOX_PREFIX + Hex.toStringCondensed(groupInboxID))
    }

    @JvmStatic
    fun getEncodedClosedGroupID(groupID: ByteArray): String {
        return CLOSED_GROUP_PREFIX + Hex.toStringCondensed(groupID)
    }

    @JvmStatic
    fun getEncodedId(group: SignalServiceGroup): String {
        val groupId = group.groupId
        if (group.groupType == SignalServiceGroup.GroupType.PUBLIC_CHAT) {
            return getEncodedOpenGroupID(groupId)
        }
        return getEncodedClosedGroupID(groupId)
    }

    private fun splitEncodedGroupID(groupID: String): String {
        if (groupID.split("!").count() > 1) {
            return groupID.split("!", limit = 2)[1]
        }
        return groupID
    }

    @JvmStatic
    fun getDecodedGroupID(groupID: String): String {
        return String(getDecodedGroupIDAsData(groupID))
    }

    @JvmStatic
    fun getDecodedGroupIDAsData(groupID: String): ByteArray {
        return Hex.fromStringCondensed(splitEncodedGroupID(groupID))
    }

    @JvmStatic
    fun getDecodedOpenGroupInboxSessionId(groupID: String): String {
        val decodedGroupId = getDecodedGroupID(groupID)
        if (decodedGroupId.split("!").count() > 2) {
            return decodedGroupId.split("!", limit = 3)[2]
        }
        return decodedGroupId
    }

    fun isEncodedGroup(groupId: String): Boolean {
        return groupId.startsWith(CLOSED_GROUP_PREFIX) || groupId.startsWith(OPEN_GROUP_PREFIX)
    }

    @JvmStatic
    fun isOpenGroup(groupId: String): Boolean {
        return groupId.startsWith(OPEN_GROUP_PREFIX)
    }

    @JvmStatic
    fun isOpenGroupInbox(groupId: String): Boolean {
        return groupId.startsWith(OPEN_GROUP_INBOX_PREFIX)
    }

    @JvmStatic
    fun isClosedGroup(groupId: String): Boolean {
        return groupId.startsWith(CLOSED_GROUP_PREFIX)
    }

    // NOTE: Signal group ID handling is weird. The ID is double encoded in the database, but not in a `GroupContext`.

    @JvmStatic
    @Throws(IOException::class)
    fun doubleEncodeGroupID(groupPublicKey: String): String {
        return getEncodedClosedGroupID(getEncodedClosedGroupID(Hex.fromStringCondensed(groupPublicKey)).toByteArray())
    }

    @JvmStatic
    fun doubleEncodeGroupID(groupID: ByteArray): String {
        return getEncodedClosedGroupID(getEncodedClosedGroupID(groupID).toByteArray())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun doubleDecodeGroupID(groupID: String): ByteArray {
        return getDecodedGroupIDAsData(getDecodedGroupID(groupID))
    }

    @JvmStatic
    @Throws(IOException::class)
    fun doubleDecodeGroupId(groupID: String): String =
        Hex.toStringCondensed(getDecodedGroupIDAsData(getDecodedGroupID(groupID)))

    @JvmStatic
    fun addressToGroupSessionId(address: Address): String =
        doubleDecodeGroupId(address.toGroupString())

    fun createConfigMemberMap(
        members: Collection<String>,
        admins: Collection<String>
    ): Map<String, Boolean> {
        // Start with admins
        val memberMap = admins.associateWith { true }.toMutableMap()

        // Add the remaining members (there may be duplicates, so only add ones that aren't already in there from admins)
        for (member in members) {
            if (member !in memberMap) {
                memberMap[member] = false
            }
        }
        return memberMap
    }
}