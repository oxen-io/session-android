package org.session.libsession.messaging.utilities

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonParseException
import org.session.libsession.messaging.messages.control.GroupUpdated
import org.session.libsignal.messages.SignalServiceGroup
import org.session.libsignal.protos.SignalServiceProtos.DataMessage.GroupUpdateInfoChangeMessage
import org.session.libsignal.protos.SignalServiceProtos.DataMessage.GroupUpdateMemberChangeMessage.Type
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import java.util.Collections

// class used to save update messages details
class UpdateMessageData () {

    var kind: Kind? = null

    //the annotations below are required for serialization. Any new Kind class MUST be declared as JsonSubTypes as well
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes(
        JsonSubTypes.Type(Kind.GroupCreation::class, name = "GroupCreation"),
        JsonSubTypes.Type(Kind.GroupNameChange::class, name = "GroupNameChange"),
        JsonSubTypes.Type(Kind.GroupMemberAdded::class, name = "GroupMemberAdded"),
        JsonSubTypes.Type(Kind.GroupMemberRemoved::class, name = "GroupMemberRemoved"),
        JsonSubTypes.Type(Kind.GroupMemberLeft::class, name = "GroupMemberLeft"),
        JsonSubTypes.Type(Kind.OpenGroupInvitation::class, name = "OpenGroupInvitation"),
        JsonSubTypes.Type(Kind.GroupAvatarUpdated::class, name = "GroupAvatarUpdated"),
        JsonSubTypes.Type(Kind.GroupMemberUpdated::class, name = "GroupMemberUpdated"),
        JsonSubTypes.Type(Kind.GroupExpirationUpdated::class, name = "GroupExpirationUpdated"),
        JsonSubTypes.Type(Kind.GroupInvitation::class, name = "GroupInvitation"),
        JsonSubTypes.Type(Kind.GroupLeaving::class, name = "GroupLeaving"),
        JsonSubTypes.Type(Kind.GroupErrorQuit::class, name = "GroupErrorQuit"),
        JsonSubTypes.Type(Kind.GroupKicked::class, name = "GroupKicked")
    )
    sealed class Kind {
        data object GroupCreation: Kind()
        class GroupNameChange(val name: String): Kind() {
            constructor(): this("") //default constructor required for json serialization
        }
        class GroupMemberAdded(val updatedMembers: Collection<String>, val groupName: String): Kind() {
            constructor(): this(Collections.emptyList(), "")
        }
        class GroupMemberRemoved(val updatedMembers: Collection<String>, val groupName:String): Kind() {
            constructor(): this(Collections.emptyList(), "")
        }
        class GroupMemberLeft(val updatedMembers: Collection<String>, val groupName:String): Kind() {
            constructor(): this(Collections.emptyList(), "")
        }
        class GroupMemberUpdated(
            val sessionIds: List<String>,
            val type: MemberUpdateType?,
            val groupName: String,
            val historyShared: Boolean
        ): Kind() {
            constructor(): this(emptyList(), null, "", false)
        }
        data object GroupAvatarUpdated: Kind()
        class GroupExpirationUpdated(val updatedExpiration: Int = 0): Kind() {
            constructor(): this(0)
        }
        class OpenGroupInvitation(val groupUrl: String, val groupName: String): Kind() {
            constructor(): this("", "")
        }
        data object GroupLeaving: Kind()
        data object GroupErrorQuit: Kind()
        class GroupInvitation(val groupAccountId: String, val invitingAdmin: String, val groupName: String) : Kind() {
            constructor(): this("", "", "")
        }

        class GroupKicked(val groupName: String) : Kind() {
            constructor(): this("")
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes(
        JsonSubTypes.Type(MemberUpdateType.ADDED::class, name = "ADDED"),
        JsonSubTypes.Type(MemberUpdateType.REMOVED::class, name = "REMOVED"),
        JsonSubTypes.Type(MemberUpdateType.PROMOTED::class, name = "PROMOTED"),
    )
    sealed class MemberUpdateType {
        data object ADDED: MemberUpdateType()
        data object REMOVED: MemberUpdateType()
        data object PROMOTED: MemberUpdateType()
    }

    constructor(kind: Kind): this() {
        this.kind = kind
    }

    companion object {
        val TAG = UpdateMessageData::class.simpleName

        fun buildGroupUpdate(type: SignalServiceGroup.Type, name: String, members: Collection<String>): UpdateMessageData? {
            return when(type) {
                SignalServiceGroup.Type.CREATION -> UpdateMessageData(Kind.GroupCreation)
                SignalServiceGroup.Type.NAME_CHANGE -> UpdateMessageData(Kind.GroupNameChange(name))
                SignalServiceGroup.Type.MEMBER_ADDED -> UpdateMessageData(Kind.GroupMemberAdded(members, name))
                SignalServiceGroup.Type.MEMBER_REMOVED -> UpdateMessageData(Kind.GroupMemberRemoved(members, name))
                SignalServiceGroup.Type.QUIT -> UpdateMessageData(Kind.GroupMemberLeft(members, name))
                SignalServiceGroup.Type.LEAVING -> UpdateMessageData(Kind.GroupLeaving)
                SignalServiceGroup.Type.ERROR_QUIT -> UpdateMessageData(Kind.GroupErrorQuit)
                SignalServiceGroup.Type.KICKED -> UpdateMessageData(Kind.GroupKicked(name))
                SignalServiceGroup.Type.UNKNOWN,
                SignalServiceGroup.Type.UPDATE,
                SignalServiceGroup.Type.DELIVER,
                SignalServiceGroup.Type.REQUEST_INFO -> null
            }
        }

        fun buildGroupUpdate(groupUpdated: GroupUpdated, groupName: String): UpdateMessageData? {
            val inner = groupUpdated.inner
            return when {
                inner.hasMemberChangeMessage() -> {
                    val memberChange = inner.memberChangeMessage
                    val type = when (memberChange.type) {
                        Type.ADDED -> MemberUpdateType.ADDED
                        Type.PROMOTED -> MemberUpdateType.PROMOTED
                        Type.REMOVED -> MemberUpdateType.REMOVED
                        null -> null
                    }
                    val members = memberChange.memberSessionIdsList
                    UpdateMessageData(Kind.GroupMemberUpdated(members, type, groupName, memberChange.historyShared))
                }
                inner.hasInfoChangeMessage() -> {
                    val infoChange = inner.infoChangeMessage
                    val type = infoChange.type
                    when (type) {
                        GroupUpdateInfoChangeMessage.Type.NAME -> Kind.GroupNameChange(infoChange.updatedName)
                        GroupUpdateInfoChangeMessage.Type.AVATAR -> Kind.GroupAvatarUpdated
                        GroupUpdateInfoChangeMessage.Type.DISAPPEARING_MESSAGES -> Kind.GroupExpirationUpdated(infoChange.updatedExpiration)
                        else -> null
                    }?.let { UpdateMessageData(it) }
                }
                inner.hasMemberLeftNotificationMessage() -> UpdateMessageData(Kind.GroupMemberLeft(
                    updatedMembers = listOf(groupUpdated.sender.orEmpty()),
                    groupName = groupName
                ))
                else -> null
            }
        }

        fun buildOpenGroupInvitation(url: String, name: String): UpdateMessageData {
            return UpdateMessageData(Kind.OpenGroupInvitation(url, name))
        }

        fun buildGroupLeaveUpdate(newType: Kind): UpdateMessageData {
            return UpdateMessageData(newType)
        }

        @JvmStatic
        fun fromJSON(json: String): UpdateMessageData? {
             return try {
                JsonUtil.fromJson(json, UpdateMessageData::class.java)
            } catch (e: JsonParseException) {
                Log.e(TAG, "${e.message}")
                null
            }
        }

    }

    fun toJSON(): String {
        return JsonUtil.toJson(this)
    }

    fun isGroupLeavingKind(): Boolean {
        return kind is Kind.GroupLeaving
    }

    fun isGroupErrorQuitKind(): Boolean {
        return kind is Kind.GroupErrorQuit
    }
}
