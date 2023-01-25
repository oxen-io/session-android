package network.loki.messenger.libsession_util

import network.loki.messenger.libsession_util.util.ConfigWithSeqNo
import network.loki.messenger.libsession_util.util.Contact
import network.loki.messenger.libsession_util.util.UserPic


sealed class ConfigBase(protected val /* yucky */ pointer: Long) {
    companion object {
        init {
            System.loadLibrary("session_util")
        }
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
    external fun getPic(): UserPic?
    external fun setPic(userPic: UserPic)
}

class ConversationConfig(pointer: Long): ConfigBase(pointer) {
    companion object {
        init {
            System.loadLibrary("session_util")
        }

        external fun newInstance(ed25519SecretKey: ByteArray): ConversationConfig

        external fun newInstance(ed25519SecretKey: ByteArray, initialDump: ByteArray): ConversationConfig

    }

    external fun todo()

}