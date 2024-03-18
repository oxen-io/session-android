package org.session.libsession.messaging.messages.control

import com.google.protobuf.ByteString
import org.session.libsession.messaging.messages.copyExpiration
import org.session.libsession.messaging.messages.visible.Profile
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Log

class MessageRequestResponse(val isApproved: Boolean, var profile: Profile? = null) : ControlMessage() {

    override val isSelfSendValid: Boolean = true

    override fun toProto(): SignalServiceProtos.Content? {
        val profileProto = SignalServiceProtos.DataMessage.LokiProfile.newBuilder()
        profile?.displayName?.let { profileProto.displayName = it }
        profile?.profilePictureURL?.let { profileProto.profilePicture = it }
        val messageRequestResponseProto = SignalServiceProtos.MessageRequestResponse.newBuilder()
            .setIsApproved(isApproved)
            .setProfile(profileProto.build())
        profile?.profileKey?.let { messageRequestResponseProto.profileKey = ByteString.copyFrom(it) }
        return try {
            SignalServiceProtos.Content.newBuilder()
                .applyExpiryMode()
                .setMessageRequestResponse(messageRequestResponseProto.build())
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct message request response proto from: $this")
            null
        }
    }

    companion object {
        const val TAG = "MessageRequestResponse"

        fun fromProto(proto: SignalServiceProtos.Content): MessageRequestResponse? {
            val messageRequestResponseProto = if (proto.hasMessageRequestResponse()) proto.messageRequestResponse else return null
            val isApproved = messageRequestResponseProto.isApproved
            val profileProto = messageRequestResponseProto.profile
            val profile = Profile().apply {
                displayName = profileProto.displayName
                profileKey = if (messageRequestResponseProto.hasProfileKey()) messageRequestResponseProto.profileKey.toByteArray() else null
                profilePictureURL = profileProto.profilePicture
            }
            return MessageRequestResponse(isApproved, profile)
                    .copyExpiration(proto)
        }
    }
}
