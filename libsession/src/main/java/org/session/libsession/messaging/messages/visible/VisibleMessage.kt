package org.session.libsession.messaging.messages.visible

import com.goterl.lazysodium.BuildConfig
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.copyExpiration
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Log
import org.session.libsession.messaging.sending_receiving.attachments.Attachment as SignalAttachment

/**
 * @param syncTarget In the case of a sync message, the public key of the person the message was targeted at.
 *
 * **Note:** `nil` if this isn't a sync message.
 */
data class VisibleMessage(
    var syncTarget: String? = null,
    var text: String? = null,
    val attachmentIDs: MutableList<Long> = mutableListOf(),
    var quote: Quote? = null,
    var linkPreview: LinkPreview? = null,
    var profile: Profile? = null,
    var openGroupInvitation: OpenGroupInvitation? = null,
    var reaction: Reaction? = null,
    var hasMention: Boolean = false,
    var blocksMessageRequests: Boolean = false
) : Message()  {

    override val isSelfSendValid: Boolean = true

    // region Validation
    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        if (attachmentIDs.isNotEmpty()) return true
        if (openGroupInvitation != null) return true
        if (reaction != null) return true
        val text = text?.trim() ?: return false
        return text.isNotEmpty()
    }
    // endregion

    // region Proto Conversion
    companion object {
        const val TAG = "VisibleMessage"

        fun fromProto(proto: SignalServiceProtos.Content): VisibleMessage? =
            proto.dataMessage?.let { VisibleMessage().apply {
                if (it.hasSyncTarget()) syncTarget = it.syncTarget
                text = it.body
                // Attachments are handled in MessageReceiver
                if (it.hasQuote()) quote = Quote.fromProto(it.quote)
                linkPreview = it.previewList.firstOrNull()?.let(LinkPreview::fromProto)
                if (it.hasOpenGroupInvitation()) openGroupInvitation = it.openGroupInvitation?.let(OpenGroupInvitation::fromProto)
                // TODO Contact
                profile = Profile.fromProto(it)
                if (it.hasReaction()) reaction = it.reaction?.let(Reaction::fromProto)
                blocksMessageRequests = it.hasBlocksCommunityMessageRequests() && it.blocksCommunityMessageRequests
            }.copyExpiration(proto)
        }
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val proto = SignalServiceProtos.Content.newBuilder()
        // Profile
        val dataMessage = profile?.toProto()?.toBuilder() ?: SignalServiceProtos.DataMessage.newBuilder()
        // Text
        if (text != null) { dataMessage.body = text }
        // Quote
        val quoteProto = quote?.toProto()
        if (quoteProto != null) {
            dataMessage.quote = quoteProto
        }
        // Reaction
        val reactionProto = reaction?.toProto()
        if (reactionProto != null) {
            dataMessage.reaction = reactionProto
        }
        // Link preview
        val linkPreviewProto = linkPreview?.toProto()
        if (linkPreviewProto != null) {
            dataMessage.addAllPreview(listOf(linkPreviewProto))
        }
        // Open group invitation
        val openGroupInvitationProto = openGroupInvitation?.toProto()
        if (openGroupInvitationProto != null) {
            dataMessage.openGroupInvitation = openGroupInvitationProto
        }
        // Attachments
        val database = MessagingModuleConfiguration.shared.messageDataProvider
        val attachments = attachmentIDs.mapNotNull { database.getSignalAttachmentPointer(it) }
        if (attachments.any { it.url.isNullOrEmpty() }) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Sending a message before all associated attachments have been uploaded.")
            }
        }
        val pointers = attachments.mapNotNull { Attachment.createAttachmentPointer(it) }
        dataMessage.addAllAttachments(pointers)
        // TODO: Contact
        // Expiration timer
        proto.applyExpiryMode()
        // Group context
        val storage = MessagingModuleConfiguration.shared.storage
        if (storage.isClosedGroup(recipient!!)) {
            try {
                dataMessage.setGroupContext()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't construct visible message proto from: $this")
                return null
            }
        }
        // Community blocked message requests flag
        dataMessage.blocksCommunityMessageRequests = blocksMessageRequests
        // Sync target
        if (syncTarget != null) {
            dataMessage.syncTarget = syncTarget
        }
        // Build
        return try {
            proto.dataMessage = dataMessage.build()
            proto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct visible message proto from: $this")
            null
        }
    }
    // endregion

    fun addSignalAttachments(signalAttachments: List<SignalAttachment>) {
        val attachmentIDs = signalAttachments.map {
            val databaseAttachment = it as DatabaseAttachment
            databaseAttachment.attachmentId.rowId
        }
        this.attachmentIDs.addAll(attachmentIDs)
    }

    fun isMediaMessage(): Boolean {
        return attachmentIDs.isNotEmpty() || quote != null || linkPreview != null || reaction != null
    }
}
