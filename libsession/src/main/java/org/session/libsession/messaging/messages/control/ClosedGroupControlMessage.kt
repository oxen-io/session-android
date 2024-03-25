package org.session.libsession.messaging.messages.control

import com.google.protobuf.ByteString
import org.session.libsignal.crypto.ecc.DjbECPrivateKey
import org.session.libsignal.crypto.ecc.DjbECPublicKey
import org.session.libsignal.crypto.ecc.ECKeyPair
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.protos.SignalServiceProtos.DataMessage
import org.session.libsignal.protos.SignalServiceProtos.DataMessage.ClosedGroupControlMessage.Type.ENCRYPTION_KEY_PAIR
import org.session.libsignal.protos.SignalServiceProtos.DataMessage.ClosedGroupControlMessage.Type.MEMBERS_ADDED
import org.session.libsignal.protos.SignalServiceProtos.DataMessage.ClosedGroupControlMessage.Type.MEMBERS_REMOVED
import org.session.libsignal.protos.SignalServiceProtos.DataMessage.ClosedGroupControlMessage.Type.MEMBER_LEFT
import org.session.libsignal.protos.SignalServiceProtos.DataMessage.ClosedGroupControlMessage.Type.NAME_CHANGE
import org.session.libsignal.protos.SignalServiceProtos.DataMessage.ClosedGroupControlMessage.Type.NEW
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.removingIdPrefixIfNeeded
import org.session.libsignal.utilities.toHexString

class ClosedGroupControlMessage() : ControlMessage() {
    var kind: Kind? = null
    var groupID: String? = null

    override val defaultTtl: Long get() {
        return when (kind) {
            is Kind.EncryptionKeyPair -> 14 * 24 * 60 * 60 * 1000
            else -> 14 * 24 * 60 * 60 * 1000
        }
    }

    override val isSelfSendValid: Boolean = true

    override fun isValid(): Boolean {
        val kind = kind
        if (!super.isValid() || kind == null) return false
        return when (kind) {
            is Kind.New -> {
                !kind.publicKey.isEmpty && kind.name.isNotEmpty() && kind.encryptionKeyPair?.publicKey != null
                    && kind.encryptionKeyPair?.privateKey != null && kind.members.isNotEmpty() && kind.admins.isNotEmpty()
                    && kind.expirationTimer >= 0
            }
            is Kind.EncryptionKeyPair -> true
            is Kind.NameChange -> kind.name.isNotEmpty()
            is Kind.MembersAdded -> kind.members.isNotEmpty()
            is Kind.MembersRemoved -> kind.members.isNotEmpty()
            is Kind.MemberLeft -> true
        }
    }

    sealed class Kind {
        class New(var publicKey: ByteString, var name: String, var encryptionKeyPair: ECKeyPair?, var members: List<ByteString>, var admins: List<ByteString>, var expirationTimer: Int) : Kind() {
            internal constructor() : this(ByteString.EMPTY, "", null, listOf(), listOf(), 0)
        }
        /** An encryption key pair encrypted for each member individually.
         *
         * **Note:** `publicKey` is only set when an encryption key pair is sent in a one-to-one context (i.e. not in a group).
         */
        class EncryptionKeyPair(var publicKey: ByteString?, var wrappers: Collection<KeyPairWrapper>) : Kind() {
            internal constructor() : this(null, listOf())
        }
        class NameChange(var name: String) : Kind() {
            internal constructor() : this("")
        }
        class MembersAdded(var members: List<ByteString>) : Kind() {
            internal constructor() : this(listOf())
        }
        class MembersRemoved(var members: List<ByteString>) : Kind() {
            internal constructor() : this(listOf())
        }
        class MemberLeft() : Kind()

        val description: String =
            when (this) {
                is New -> "new"
                is EncryptionKeyPair -> "encryptionKeyPair"
                is NameChange -> "nameChange"
                is MembersAdded -> "membersAdded"
                is MembersRemoved -> "membersRemoved"
                is MemberLeft -> "memberLeft"
            }
    }

    companion object {
        const val TAG = "ClosedGroupControlMessage"

        fun fromProto(proto: SignalServiceProtos.Content): ClosedGroupControlMessage? =
            proto.takeIf { it.hasDataMessage() }?.dataMessage
                ?.takeIf { it.hasClosedGroupControlMessage() }?.closedGroupControlMessage
                ?.run {
                    when (type) {
                        NEW -> takeIf { it.hasPublicKey() && it.hasEncryptionKeyPair() && it.hasName() }?.let {
                            ECKeyPair(
                                DjbECPublicKey(encryptionKeyPair.publicKey.toByteArray()),
                                DjbECPrivateKey(encryptionKeyPair.privateKey.toByteArray())
                            ).let { Kind.New(publicKey, name, it, membersList, adminsList, expirationTimer) }
                        }
                        ENCRYPTION_KEY_PAIR -> Kind.EncryptionKeyPair(publicKey, wrappersList.mapNotNull(KeyPairWrapper::fromProto))
                        NAME_CHANGE -> takeIf { it.hasName() }?.let { Kind.NameChange(name) }
                        MEMBERS_ADDED -> Kind.MembersAdded(membersList)
                        MEMBERS_REMOVED -> Kind.MembersRemoved(membersList)
                        MEMBER_LEFT -> Kind.MemberLeft()
                        else -> null
                    }?.let(::ClosedGroupControlMessage)
             }
    }

    internal constructor(kind: Kind?, groupID: String? = null) : this() {
        this.kind = kind
        this.groupID = groupID
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val kind = kind
        if (kind == null) {
            Log.w(TAG, "Couldn't construct closed group control message proto from: $this.")
            return null
        }
        try {
            val closedGroupControlMessage: DataMessage.ClosedGroupControlMessage.Builder = DataMessage.ClosedGroupControlMessage.newBuilder()
            when (kind) {
                is Kind.New -> {
                    closedGroupControlMessage.type = NEW
                    closedGroupControlMessage.publicKey = kind.publicKey
                    closedGroupControlMessage.name = kind.name
                    closedGroupControlMessage.encryptionKeyPair = SignalServiceProtos.KeyPair.newBuilder().also {
                        it.publicKey = ByteString.copyFrom(kind.encryptionKeyPair!!.publicKey.serialize().removingIdPrefixIfNeeded())
                        it.privateKey = ByteString.copyFrom(kind.encryptionKeyPair!!.privateKey.serialize())
                    }.build()
                    closedGroupControlMessage.addAllMembers(kind.members)
                    closedGroupControlMessage.addAllAdmins(kind.admins)
                    closedGroupControlMessage.expirationTimer = kind.expirationTimer
                }
                is Kind.EncryptionKeyPair -> {
                    closedGroupControlMessage.type = ENCRYPTION_KEY_PAIR
                    closedGroupControlMessage.publicKey = kind.publicKey ?: ByteString.EMPTY
                    closedGroupControlMessage.addAllWrappers(kind.wrappers.map { it.toProto() })
                }
                is Kind.NameChange -> {
                    closedGroupControlMessage.type = NAME_CHANGE
                    closedGroupControlMessage.name = kind.name
                }
                is Kind.MembersAdded -> {
                    closedGroupControlMessage.type = MEMBERS_ADDED
                    closedGroupControlMessage.addAllMembers(kind.members)
                }
                is Kind.MembersRemoved -> {
                    closedGroupControlMessage.type = MEMBERS_REMOVED
                    closedGroupControlMessage.addAllMembers(kind.members)
                }
                is Kind.MemberLeft -> {
                    closedGroupControlMessage.type = MEMBER_LEFT
                }
            }
            return SignalServiceProtos.Content.newBuilder().apply {
                dataMessage = DataMessage.newBuilder().also {
                    it.closedGroupControlMessage = closedGroupControlMessage.build()
                    it.setGroupContext()
                }.build()
            }.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct closed group control message proto from: $this.")
            return null
        }
    }

    class KeyPairWrapper(val publicKey: String?, val encryptedKeyPair: ByteString?) {

        val isValid: Boolean = run {
            this.publicKey != null && this.encryptedKeyPair != null
        }

        companion object {

            fun fromProto(proto: DataMessage.ClosedGroupControlMessage.KeyPairWrapper): KeyPairWrapper {
                return KeyPairWrapper(proto.publicKey.toByteArray().toHexString(), proto.encryptedKeyPair)
            }
        }

        fun toProto(): DataMessage.ClosedGroupControlMessage.KeyPairWrapper? {
            val result = DataMessage.ClosedGroupControlMessage.KeyPairWrapper.newBuilder()
            result.publicKey = ByteString.copyFrom(Hex.fromStringCondensed(publicKey ?: return null))
            result.encryptedKeyPair = encryptedKeyPair ?: return null
            return try {
                result.build()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't construct key pair wrapper proto from: $this")
                return null
            }
        }
    }
}
