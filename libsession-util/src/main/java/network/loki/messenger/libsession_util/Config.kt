package network.loki.messenger.libsession_util

import network.loki.messenger.libsession_util.util.ConfigWithSeqNo
import network.loki.messenger.libsession_util.util.Contact
import network.loki.messenger.libsession_util.util.Conversation
import network.loki.messenger.libsession_util.util.UserPic
import org.session.libsignal.protos.SignalServiceProtos.SharedConfigMessage.Kind


sealed class ConfigBase(protected val /* yucky */ pointer: Long) {
    companion object {
        init {
            System.loadLibrary("session_util")
        }
        external fun kindFor(configNamespace: Int): Class<ConfigBase>

        fun ConfigBase.protoKindFor(): Kind = when (this) {
            is UserProfile -> Kind.USER_PROFILE
            is Contacts -> Kind.CONTACTS
            is ConversationVolatileConfig -> Kind.CONVO_INFO_VOLATILE
        }

        const val isNewConfigEnabled = true

    }

    external fun dirty(): Boolean
    external fun needsPush(): Boolean
    external fun needsDump(): Boolean
    external fun push(): ConfigWithSeqNo
    external fun dump(): ByteArray
    external fun encryptionDomain(): String
    external fun confirmPushed(seqNo: Long)
    external fun merge(toMerge: Array<ByteArray>): Int

    external fun configNamespace(): Int

    // Singular merge
    external fun merge(toMerge: ByteArray): Int

    external fun free()

    @Override
    fun finalize() {
        free()
    }

}

class Contacts(pointer: Long) : ConfigBase(pointer) {
    companion object {
        init {
            System.loadLibrary("session_util")
        }
        external fun newInstance(ed25519SecretKey: ByteArray): Contacts
        external fun newInstance(ed25519SecretKey: ByteArray, initialDump: ByteArray): Contacts
    }

    external fun get(sessionId: String): Contact?
    external fun getOrConstruct(sessionId: String): Contact
    external fun all(): List<Contact>
    external fun set(contact: Contact)
    external fun erase(sessionId: String): Boolean
}

class UserProfile(pointer: Long) : ConfigBase(pointer) {
    companion object {
        init {
            System.loadLibrary("session_util")
        }
        external fun newInstance(ed25519SecretKey: ByteArray): UserProfile
        external fun newInstance(ed25519SecretKey: ByteArray, initialDump: ByteArray): UserProfile
    }

    external fun setName(newName: String)
    external fun getName(): String?
    external fun getPic(): UserPic
    external fun setPic(userPic: UserPic)
}

class ConversationVolatileConfig(pointer: Long): ConfigBase(pointer) {
    companion object {
        init {
            System.loadLibrary("session_util")
        }
        external fun newInstance(ed25519SecretKey: ByteArray): ConversationVolatileConfig
        external fun newInstance(ed25519SecretKey: ByteArray, initialDump: ByteArray): ConversationVolatileConfig
    }

    external fun getOneToOne(pubKeyHex: String): Conversation.OneToOne?
    external fun getOrConstructOneToOne(pubKeyHex: String): Conversation.OneToOne
    external fun eraseOneToOne(pubKeyHex: String): Boolean

    external fun getCommunity(baseUrl: String, room: String): Conversation.Community?
    external fun getOrConstructCommunity(baseUrl: String, room: String, pubKeyHex: String): Conversation.Community
    external fun getOrConstructCommunity(baseUrl: String, room: String, pubKey: ByteArray): Conversation.Community
    external fun eraseCommunity(community: Conversation.Community): Boolean
    external fun eraseCommunity(baseUrl: String, room: String): Boolean

    external fun getLegacyClosedGroup(groupId: String): Conversation.LegacyGroup?
    external fun getOrConstructLegacyClosedGroup(groupId: String): Conversation.LegacyGroup
    external fun eraseLegacyClosedGroup(groupId: String): Boolean
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
    external fun all(): List<Conversation>

}