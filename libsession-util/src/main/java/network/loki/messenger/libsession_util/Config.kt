package network.loki.messenger.libsession_util

import network.loki.messenger.libsession_util.util.BaseCommunityInfo
import network.loki.messenger.libsession_util.util.ConfigPush
import network.loki.messenger.libsession_util.util.Contact
import network.loki.messenger.libsession_util.util.Conversation
import network.loki.messenger.libsession_util.util.ExpiryMode
import network.loki.messenger.libsession_util.util.GroupInfo
import network.loki.messenger.libsession_util.util.GroupMember
import network.loki.messenger.libsession_util.util.UserPic
import org.session.libsignal.protos.SignalServiceProtos.SharedConfigMessage.Kind
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.session.libsignal.utilities.SessionId
import java.io.Closeable
import java.util.Stack

sealed class Config(protected val pointer: Long): Closeable {
    abstract fun namespace(): Int
    external fun free()
    override fun close() {
        free()
    }
}

sealed class ConfigBase(pointer: Long): Config(pointer) {
    companion object {
        init {
            System.loadLibrary("session_util")
        }
        external fun kindFor(configNamespace: Int): Class<ConfigBase>

        fun ConfigBase.protoKindFor(): Kind = when (this) {
            is UserProfile -> Kind.USER_PROFILE
            is Contacts -> Kind.CONTACTS
            is ConversationVolatileConfig -> Kind.CONVO_INFO_VOLATILE
            is UserGroupsConfig -> Kind.GROUPS
            is GroupInfoConfig -> Kind.CLOSED_GROUP_INFO
            is GroupMembersConfig -> Kind.CLOSED_GROUP_MEMBERS
        }

        const val PRIORITY_HIDDEN = -1L
        const val PRIORITY_VISIBLE = 0L
        const val PRIORITY_PINNED = 1L

    }

    external fun dirty(): Boolean
    external fun needsPush(): Boolean
    external fun needsDump(): Boolean
    external fun push(): ConfigPush
    external fun dump(): ByteArray
    external fun encryptionDomain(): String
    external fun confirmPushed(seqNo: Long, newHash: String)
    external fun merge(toMerge: Array<Pair<String,ByteArray>>): Stack<String>
    external fun currentHashes(): List<String>

    // Singular merge
    external fun merge(toMerge: Pair<String,ByteArray>): Stack<String>

}

class Contacts(pointer: Long) : ConfigBase(pointer) {
    companion object {
        init {
            System.loadLibrary("session_util")
        }
        external fun newInstance(ed25519SecretKey: ByteArray): Contacts
        external fun newInstance(ed25519SecretKey: ByteArray, initialDump: ByteArray): Contacts
    }

    override fun namespace() = Namespace.CONTACTS()

    external fun get(sessionId: String): Contact?
    external fun getOrConstruct(sessionId: String): Contact
    external fun all(): List<Contact>
    external fun set(contact: Contact)
    external fun erase(sessionId: String): Boolean

    /**
     * Similar to [updateIfExists], but will create the underlying contact if it doesn't exist before passing to [updateFunction]
     */
    fun upsertContact(sessionId: String, updateFunction: Contact.()->Unit = {}) {
        if (sessionId.startsWith(IdPrefix.BLINDED.value)) {
            Log.w("Loki", "Trying to create a contact with a blinded ID prefix")
            return
        } else if (sessionId.startsWith(IdPrefix.UN_BLINDED.value)) {
            Log.w("Loki", "Trying to create a contact with an un-blinded ID prefix")
            return
        } else if (sessionId.startsWith(IdPrefix.BLINDEDV2.value)) {
            Log.w("Loki", "Trying to create a contact with a blindedv2 ID prefix")
            return
        }
        val contact = getOrConstruct(sessionId)
        updateFunction(contact)
        set(contact)
    }

    /**
     * Updates the contact by sessionId with a given [updateFunction], and applies to the underlying config.
     * the [updateFunction] doesn't run if there is no contact
     */
    fun updateIfExists(sessionId: String, updateFunction: Contact.()->Unit) {
        if (sessionId.startsWith(IdPrefix.BLINDED.value)) {
            Log.w("Loki", "Trying to create a contact with a blinded ID prefix")
            return
        } else if (sessionId.startsWith(IdPrefix.UN_BLINDED.value)) {
            Log.w("Loki", "Trying to create a contact with an un-blinded ID prefix")
            return
        } else if (sessionId.startsWith(IdPrefix.BLINDEDV2.value)) {
            Log.w("Loki", "Trying to create a contact with a blindedv2 ID prefix")
            return
        }
        val contact = get(sessionId) ?: return
        updateFunction(contact)
        set(contact)
    }
}

class UserProfile(pointer: Long) : ConfigBase(pointer) {
    companion object {
        init {
            System.loadLibrary("session_util")
        }
        external fun newInstance(ed25519SecretKey: ByteArray): UserProfile
        external fun newInstance(ed25519SecretKey: ByteArray, initialDump: ByteArray): UserProfile
    }

    override fun namespace() = Namespace.USER_PROFILE()

    external fun setName(newName: String)
    external fun getName(): String?
    external fun getPic(): UserPic
    external fun setPic(userPic: UserPic)
    external fun setNtsPriority(priority: Long)
    external fun getNtsPriority(): Long
    external fun setNtsExpiry(expiryMode: ExpiryMode)
    external fun getNtsExpiry(): ExpiryMode
    external fun getCommunityMessageRequests(): Boolean
    external fun setCommunityMessageRequests(blocks: Boolean)
    external fun isBlockCommunityMessageRequestsSet(): Boolean
}

class ConversationVolatileConfig(pointer: Long): ConfigBase(pointer) {
    companion object {
        init {
            System.loadLibrary("session_util")
        }
        external fun newInstance(ed25519SecretKey: ByteArray): ConversationVolatileConfig
        external fun newInstance(ed25519SecretKey: ByteArray, initialDump: ByteArray): ConversationVolatileConfig
    }

    override fun namespace() = Namespace.CONVO_INFO_VOLATILE()

    external fun getOneToOne(pubKeyHex: String): Conversation.OneToOne?
    external fun getOrConstructOneToOne(pubKeyHex: String): Conversation.OneToOne
    external fun eraseOneToOne(pubKeyHex: String): Boolean

    external fun getCommunity(baseUrl: String, room: String): Conversation.Community?
    external fun getOrConstructCommunity(baseUrl: String, room: String, pubKeyHex: String): Conversation.Community
    external fun getOrConstructCommunity(baseUrl: String, room: String, pubKey: ByteArray): Conversation.Community
    external fun eraseCommunity(community: Conversation.Community): Boolean
    external fun eraseCommunity(baseUrl: String, room: String): Boolean

    external fun getLegacyClosedGroup(groupId: String): Conversation.LegacyGroup?
    external fun getOrConstructLegacyGroup(groupId: String): Conversation.LegacyGroup
    external fun eraseLegacyClosedGroup(groupId: String): Boolean

    external fun getClosedGroup(sessionId: String): Conversation.ClosedGroup?
    external fun getOrConstructClosedGroup(sessionId: String): Conversation.ClosedGroup
    external fun eraseClosedGroup(sessionId: String): Boolean

    external fun erase(conversation: Conversation): Boolean
    external fun set(toStore: Conversation)

    /**
     * Erase all conversations that do not satisfy the `predicate`, similar to [MutableList.removeAll]
     */
    external fun eraseAll(predicate: (Conversation) -> Boolean): Int

    external fun sizeOneToOnes(): Int
    external fun sizeCommunities(): Int
    external fun sizeLegacyClosedGroups(): Int
    external fun size(): Int

    external fun empty(): Boolean

    external fun allOneToOnes(): List<Conversation.OneToOne>
    external fun allCommunities(): List<Conversation.Community>
    external fun allLegacyClosedGroups(): List<Conversation.LegacyGroup>
    external fun allClosedGroups(): List<Conversation.ClosedGroup>
    external fun all(): List<Conversation>

}

class UserGroupsConfig(pointer: Long): ConfigBase(pointer) {
    companion object {
        init {
            System.loadLibrary("session_util")
        }
        external fun newInstance(ed25519SecretKey: ByteArray): UserGroupsConfig
        external fun newInstance(ed25519SecretKey: ByteArray, initialDump: ByteArray): UserGroupsConfig
    }

    override fun namespace() = Namespace.GROUPS()

    external fun getCommunityInfo(baseUrl: String, room: String): GroupInfo.CommunityGroupInfo?
    external fun getLegacyGroupInfo(sessionId: String): GroupInfo.LegacyGroupInfo?
    external fun getClosedGroup(sessionId: String): GroupInfo.ClosedGroupInfo?
    external fun getOrConstructCommunityInfo(baseUrl: String, room: String, pubKeyHex: String): GroupInfo.CommunityGroupInfo
    external fun getOrConstructLegacyGroupInfo(sessionId: String): GroupInfo.LegacyGroupInfo
    external fun getOrConstructClosedGroup(sessionId: String): GroupInfo.ClosedGroupInfo
    external fun set(groupInfo: GroupInfo)
    external fun erase(groupInfo: GroupInfo)
    external fun eraseCommunity(baseCommunityInfo: BaseCommunityInfo): Boolean
    external fun eraseCommunity(server: String, room: String): Boolean
    external fun eraseLegacyGroup(sessionId: String): Boolean
    external fun eraseClosedGroup(sessionId: String): Boolean
    external fun sizeCommunityInfo(): Long
    external fun sizeLegacyGroupInfo(): Long
    external fun sizeClosedGroup(): Long
    external fun size(): Long
    external fun all(): List<GroupInfo>
    external fun allCommunityInfo(): List<GroupInfo.CommunityGroupInfo>
    external fun allLegacyGroupInfo(): List<GroupInfo.LegacyGroupInfo>
    external fun allClosedGroupInfo(): List<GroupInfo.ClosedGroupInfo>
    external fun createGroup(): GroupInfo.ClosedGroupInfo
}

class GroupInfoConfig(pointer: Long): ConfigBase(pointer), Closeable {
    companion object {
        init {
            System.loadLibrary("session_util")
        }

        external fun newInstance(
            pubKey: ByteArray,
            secretKey: ByteArray = byteArrayOf(),
            initialDump: ByteArray = byteArrayOf()
        ): GroupInfoConfig
    }

    override fun namespace() = Namespace.CLOSED_GROUP_INFO()

    external fun id(): SessionId
    external fun destroyGroup()
    external fun getCreated(): Long?
    external fun getDeleteAttachmentsBefore(): Long?
    external fun getDeleteBefore(): Long?
    external fun getExpiryTimer(): Long
    external fun getName(): String
    external fun getProfilePic(): UserPic
    external fun isDestroyed(): Boolean
    external fun setCreated(createdAt: Long)
    external fun setDeleteAttachmentsBefore(deleteBefore: Long)
    external fun setDeleteBefore(deleteBefore: Long)
    external fun setExpiryTimer(expireSeconds: Long)
    external fun setName(newName: String)
    external fun getDescription(): String
    external fun setDescription(newDescription: String)
    external fun setProfilePic(newProfilePic: UserPic)
    external fun storageNamespace(): Long

    override fun close() {
        free()
    }
}

class GroupMembersConfig(pointer: Long): ConfigBase(pointer), Closeable {
    companion object {
        init {
            System.loadLibrary("session_util")
        }
        external fun newInstance(
            pubKey: ByteArray,
            secretKey: ByteArray = byteArrayOf(),
            initialDump: ByteArray = byteArrayOf()
        ): GroupMembersConfig
    }

    override fun namespace() = Namespace.CLOSED_GROUP_MEMBERS()

    external fun all(): Stack<GroupMember>
    external fun erase(groupMember: GroupMember): Boolean
    external fun erase(pubKeyHex: String): Boolean
    external fun get(pubKeyHex: String): GroupMember?
    external fun getOrConstruct(pubKeyHex: String): GroupMember
    external fun set(groupMember: GroupMember)
    override fun close() {
        free()
    }
}

sealed class ConfigSig(pointer: Long) : Config(pointer)

class GroupKeysConfig(pointer: Long): ConfigSig(pointer) {
    companion object {
        init {
            System.loadLibrary("session_util")
        }
        external fun newInstance(
            userSecretKey: ByteArray,
            groupPublicKey: ByteArray,
            groupSecretKey: ByteArray = byteArrayOf(),
            initialDump: ByteArray = byteArrayOf(),
            info: GroupInfoConfig,
            members: GroupMembersConfig
        ): GroupKeysConfig
    }

    override fun namespace() = Namespace.ENCRYPTION_KEYS()

    external fun groupKeys(): Stack<ByteArray>
    external fun needsDump(): Boolean
    external fun dump(): ByteArray
    external fun loadKey(message: ByteArray,
                         hash: String,
                         timestampMs: Long,
                         info: GroupInfoConfig,
                         members: GroupMembersConfig): Boolean
    external fun needsRekey(): Boolean
    external fun pendingKey(): ByteArray?
    external fun supplementFor(userSessionId: String): ByteArray
    external fun pendingConfig(): ByteArray?
    external fun currentHashes(): List<String>
    external fun rekey(info: GroupInfoConfig, members: GroupMembersConfig): ByteArray
    override fun close() {
        free()
    }

    external fun encrypt(plaintext: ByteArray): ByteArray
    external fun decrypt(ciphertext: ByteArray): Pair<ByteArray, SessionId>?

    external fun keys(): Stack<ByteArray>

    external fun makeSubAccount(sessionId: SessionId, canWrite: Boolean = true, canDelete: Boolean = false): ByteArray
    external fun getSubAccountToken(sessionId: SessionId, canWrite: Boolean = true, canDelete: Boolean = false): ByteArray

    external fun subAccountSign(message: ByteArray, signingValue: ByteArray): SwarmAuth

    external fun currentGeneration(): Int

    data class SwarmAuth(
        val subAccount: String,
        val subAccountSig: String,
        val signature: String
    )

}