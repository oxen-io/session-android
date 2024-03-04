package org.session.libsession.messaging.messages.control

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.copyExpiration
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.protos.SignalServiceProtos.DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE
import org.session.libsignal.utilities.Log

/** In the case of a sync message, the public key of the person the message was targeted at.
 *
 * **Note:** `nil` if this isn't a sync message.
 */
data class ExpirationTimerUpdate(var syncTarget: String? = null, val isGroup: Boolean = false) : ControlMessage() {
    override val isSelfSendValid: Boolean = true

    companion object {
        const val TAG = "ExpirationTimerUpdate"
        private val storage = MessagingModuleConfiguration.shared.storage

        fun fromProto(proto: SignalServiceProtos.Content): ExpirationTimerUpdate? =
            proto.dataMessage?.takeIf { it.flags and EXPIRATION_TIMER_UPDATE_VALUE != 0 }?.run {
                ExpirationTimerUpdate(takeIf { hasSyncTarget() }?.syncTarget, hasGroup()).copyExpiration(proto)
            }
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val dataMessageProto = SignalServiceProtos.DataMessage.newBuilder().apply {
            flags = EXPIRATION_TIMER_UPDATE_VALUE
            expireTimer = expiryMode.expirySeconds.toInt()
        }
        // Sync target
        syncTarget?.let { dataMessageProto.syncTarget = it }
        // Group context
        if (storage.isClosedGroup(recipient!!)) {
            try {
                dataMessageProto.setGroupContext()
            } catch(e: Exception) {
                Log.w(TAG, "Couldn't construct visible message proto from: $this", e)
                return null
            }
        }
        return try {
            SignalServiceProtos.Content.newBuilder()
                .setDataMessage(dataMessageProto)
                .applyExpiryMode()
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct expiration timer update proto from: $this", e)
            null
        }
    }
}
