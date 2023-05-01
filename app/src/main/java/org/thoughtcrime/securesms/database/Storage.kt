package org.thoughtcrime.securesms.database

import android.content.Context
import android.net.Uri
import org.session.libsession.avatars.AvatarHelper
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.BlindedIdMapping
import org.session.libsession.messaging.calls.CallMessageType
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.jobs.*
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.control.ConfigurationMessage
import org.session.libsession.messaging.messages.control.MessageRequestResponse
import org.session.libsession.messaging.messages.signal.*
import org.session.libsession.messaging.messages.visible.Attachment
import org.session.libsession.messaging.messages.visible.Profile
import org.session.libsession.messaging.messages.visible.Reaction
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.open_groups.GroupMember
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentId
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.messaging.sending_receiving.data_extraction.DataExtractionNotificationInfoMessage
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel
import org.session.libsession.messaging.utilities.SessionId
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.messaging.utilities.UpdateMessageData
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.utilities.*
import org.session.libsession.utilities.Address.Companion.fromSerialized
import org.session.libsession.utilities.GroupRecord
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.ProfileKeyUtil
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.crypto.ecc.ECKeyPair
import org.session.libsignal.messages.SignalServiceAttachmentPointer
import org.session.libsignal.messages.SignalServiceGroup
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.KeyHelper
import org.session.libsignal.utilities.guava.Optional
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.SessionMetaProtocol
import java.security.MessageDigest

class Storage(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), StorageProtocol {

    private val smsDatabase = DatabaseComponent.get(context).smsDatabase()
    private val mmsDatabase = DatabaseComponent.get(context).mmsDatabase()
    private val mmsSmsDatabase = DatabaseComponent.get(context).mmsSmsDatabase()

    private val recipientDatabase = DatabaseComponent.get(context).recipientDatabase()

    private val attachmentDatabase = DatabaseComponent.get(context).attachmentDatabase()
    private val sessionJobDatabase = DatabaseComponent.get(context).sessionJobDatabase()

    private val threadDatabase = DatabaseComponent.get(context).threadDatabase()
    private val lokiThreadDatabase = DatabaseComponent.get(context).lokiThreadDatabase()
    private val lokiAPIDatabase = DatabaseComponent.get(context).lokiAPIDatabase()
    private val lokiMessageDatabase = DatabaseComponent.get(context).lokiMessageDatabase()

    private val blindedIdMappingDatabase = DatabaseComponent.get(context).blindedIdMappingDatabase()
    private val reactionDatabase = DatabaseComponent.get(context).reactionDatabase()
    private val groupDatabase = DatabaseComponent.get(context).groupDatabase()
    private val groupMemberDatabase = DatabaseComponent.get(context).groupMemberDatabase()
    private val sessionContactDatabase = DatabaseComponent.get(context).sessionContactDatabase()

    private fun getMmsDatabaseElseSms(isMms: Boolean) = if (isMms) mmsDatabase else smsDatabase

    override fun getUserPublicKey(): String? = TextSecurePreferences.getLocalNumber(context)

    override fun getUserX25519KeyPair(): ECKeyPair = lokiAPIDatabase.getUserX25519KeyPair()

    override fun getUserProfile(): Profile {
        val displayName = TextSecurePreferences.getProfileName(context)!!
        val profileKey = ProfileKeyUtil.getProfileKey(context)
        val profilePictureUrl = TextSecurePreferences.getProfilePictureURL(context)
        return Profile(displayName, profileKey, profilePictureUrl)
    }

    override fun setUserProfilePictureURL(newValue: String) {
        val ourRecipient = getUserPublicKey()!!.let(::fromSerialized)
            .let { Recipient.from(context, it, false) }
        TextSecurePreferences.setProfilePictureURL(context, newValue)
        RetrieveProfileAvatarJob(ourRecipient, newValue)
        ApplicationContext.getInstance(context).jobManager.add(RetrieveProfileAvatarJob(ourRecipient, newValue))
    }

    override fun getOrGenerateRegistrationID(): Int =
        TextSecurePreferences.getLocalRegistrationId(context).takeUnless { it == 0 }
            ?: KeyHelper.generateRegistrationId(false)
                .also { TextSecurePreferences.setLocalRegistrationId(context, it) }

    override fun persistAttachments(messageID: Long, attachments: List<Attachment>): List<Long> =
        attachments.mapNotNull { it.toSignalAttachment() }
            .let { attachmentDatabase.insertAttachments(messageID, it) }

    override fun getAttachmentsForMessage(messageID: Long): List<DatabaseAttachment> =
        attachmentDatabase.getAttachmentsForMessage(messageID)

    override fun markConversationAsRead(threadId: Long, updateLastSeen: Boolean) {
        threadDatabase.setRead(threadId, updateLastSeen)
    }

    override fun incrementUnread(threadId: Long, amount: Int, unreadMentionAmount: Int) {
        threadDatabase.incrementUnread(threadId, amount, unreadMentionAmount)
    }

    override fun updateThread(threadId: Long, unarchive: Boolean) {
        threadDatabase.update(threadId, unarchive)
    }

    override fun persist(message: VisibleMessage,
                         quotes: QuoteModel?,
                         linkPreview: List<LinkPreview?>,
                         groupPublicKey: String?,
                         openGroupID: String?,
                         attachments: List<Attachment>,
                         runIncrement: Boolean,
                         runThreadUpdate: Boolean): Long? {
        var messageID: Long? = null
        val senderAddress = fromSerialized(message.sender!!)
        val isUserSender = (message.sender!! == getUserPublicKey())
        val isUserBlindedSender = message.threadID?.takeIf { it >= 0 }?.let { getOpenGroup(it)?.publicKey }
            ?.let { SodiumUtilities.sessionId(getUserPublicKey()!!, message.sender!!, it) } ?: false
        val group = when {
            openGroupID != null -> SignalServiceGroup(openGroupID.toByteArray(), SignalServiceGroup.GroupType.PUBLIC_CHAT)
            groupPublicKey != null -> {
                val doubleEncoded = GroupUtil.doubleEncodeGroupID(groupPublicKey)
                SignalServiceGroup(GroupUtil.getDecodedGroupIDAsData(doubleEncoded), SignalServiceGroup.GroupType.SIGNAL)
            }
            else -> null
        }
        val pointers = attachments.mapNotNull(Attachment::toSignalAttachment)

        val targetAddress = message.syncTarget
            ?.takeIf { (isUserSender || isUserBlindedSender) && it.isNotEmpty() }
            ?.let(::fromSerialized)
            ?: group?.let(GroupUtil::getEncodedId)?.let(::fromSerialized) ?: senderAddress

        val targetRecipient = Recipient.from(context, targetAddress, false)
        if (!targetRecipient.isGroupRecipient) {
            if (isUserSender || isUserBlindedSender) {
                recipientDatabase.setApproved(targetRecipient, true)
            } else {
                recipientDatabase.setApprovedMe(targetRecipient, true)
            }
        }
        if (message.isMediaMessage() || attachments.isNotEmpty()) {
            val quote: Optional<QuoteModel> = if (quotes != null) Optional.of(quotes) else Optional.absent()
            val linkPreviews: Optional<List<LinkPreview>> = if (linkPreview.isEmpty()) Optional.absent() else Optional.of(linkPreview.mapNotNull { it!! })
            val insertResult = if (isUserSender || isUserBlindedSender) {
                val mediaMessage = OutgoingMediaMessage.from(message, targetRecipient, pointers, quote.orNull(), linkPreviews.orNull()?.firstOrNull())
                mmsDatabase.insertSecureDecryptedMessageOutbox(mediaMessage, message.threadID ?: -1, message.sentTimestamp!!, runThreadUpdate)
            } else {
                // It seems like we have replaced SignalServiceAttachment with SessionServiceAttachment
                val signalServiceAttachments = attachments.mapNotNull {
                    it.toSignalPointer()
                }
                val mediaMessage = IncomingMediaMessage.from(message, senderAddress, targetRecipient.expireMessages * 1000L, Optional.fromNullable(group), signalServiceAttachments, quote, linkPreviews)
                mmsDatabase.insertSecureDecryptedMessageInbox(mediaMessage, message.threadID ?: -1, message.receivedTimestamp ?: 0, runIncrement, runThreadUpdate)
            }
            if (insertResult.isPresent) {
                messageID = insertResult.get().messageId
            }
        } else {
            val isOpenGroupInvitation = (message.openGroupInvitation != null)

            val insertResult = if (isUserSender || isUserBlindedSender) {
                val textMessage = if (isOpenGroupInvitation) OutgoingTextMessage.fromOpenGroupInvitation(message.openGroupInvitation, targetRecipient, message.sentTimestamp)
                else OutgoingTextMessage.from(message, targetRecipient)
                smsDatabase.insertMessageOutbox(message.threadID ?: -1, textMessage, message.sentTimestamp!!, runThreadUpdate)
            } else {
                val textMessage = if (isOpenGroupInvitation) IncomingTextMessage.fromOpenGroupInvitation(message.openGroupInvitation, senderAddress, message.sentTimestamp)
                else IncomingTextMessage.from(message, senderAddress, Optional.fromNullable(group), targetRecipient.expireMessages * 1000L)
                val encrypted = IncomingEncryptedMessage(textMessage, textMessage.messageBody)
                smsDatabase.insertMessageInbox(encrypted, message.receivedTimestamp ?: 0, runIncrement, runThreadUpdate)
            }
            insertResult.orNull()?.let { result ->
                messageID = result.messageId
            }
        }
        message.serverHash?.let { serverHash ->
            messageID?.let { id ->
                lokiMessageDatabase.setMessageServerHash(id, serverHash)
            }
        }
        return messageID
    }

    override fun persistJob(job: Job) {
        sessionJobDatabase.persistJob(job)
    }

    override fun markJobAsSucceeded(jobId: String) {
        sessionJobDatabase.markJobAsSucceeded(jobId)
    }

    override fun markJobAsFailedPermanently(jobId: String) {
        sessionJobDatabase.markJobAsFailedPermanently(jobId)
    }

    override fun getAllPendingJobs(type: String): Map<String, Job?> =
        sessionJobDatabase.getAllPendingJobs(type)

    override fun getAttachmentUploadJob(attachmentID: Long): AttachmentUploadJob? =
        sessionJobDatabase.getAttachmentUploadJob(attachmentID)

    override fun getMessageSendJob(messageSendJobID: String): MessageSendJob? =
        sessionJobDatabase.getMessageSendJob(messageSendJobID)

    override fun getMessageReceiveJob(messageReceiveJobID: String): MessageReceiveJob? =
        sessionJobDatabase.getMessageReceiveJob(messageReceiveJobID)

    override fun getGroupAvatarDownloadJob(server: String, room: String, imageId: String?): GroupAvatarDownloadJob? =
        sessionJobDatabase.getGroupAvatarDownloadJob(server, room, imageId)

    override fun resumeMessageSendJobIfNeeded(messageSendJobID: String) {
        sessionJobDatabase.getMessageSendJob(messageSendJobID)
            ?.let(JobQueue.shared::resumePendingSendMessage)
    }

    override fun isJobCanceled(job: Job): Boolean = sessionJobDatabase.isJobCanceled(job)

    override fun getAuthToken(room: String, server: String): String? {
        val id = "$server.$room"
        return lokiAPIDatabase.getAuthToken(id)
    }

    override fun setAuthToken(room: String, server: String, newValue: String) {
        val id = "$server.$room"
        lokiAPIDatabase.setAuthToken(id, newValue)
    }

    override fun removeAuthToken(room: String, server: String) {
        val id = "$server.$room"
        lokiAPIDatabase.setAuthToken(id, null)
    }

    override fun getOpenGroup(threadId: Long): OpenGroup? {
        if (threadId < 0) { return null }
        return databaseHelper.readableDatabase.get(LokiThreadDatabase.publicChatTable, "${LokiThreadDatabase.threadID} = ?", arrayOf( threadId.toString() )) { cursor ->
            val publicChatAsJson = cursor.getString(LokiThreadDatabase.publicChat)
            OpenGroup.fromJSON(publicChatAsJson)
        }
    }

    override fun getOpenGroupPublicKey(server: String): String? =
        lokiAPIDatabase.getOpenGroupPublicKey(server)

    override fun setOpenGroupPublicKey(server: String, newValue: String) {
        lokiAPIDatabase.setOpenGroupPublicKey(server, newValue)
    }

    override fun getLastMessageServerID(room: String, server: String): Long? =
        lokiAPIDatabase.getLastMessageServerID(room, server)

    override fun setLastMessageServerID(room: String, server: String, newValue: Long) {
        lokiAPIDatabase.setLastMessageServerID(room, server, newValue)
    }

    override fun removeLastMessageServerID(room: String, server: String) {
        lokiAPIDatabase.removeLastMessageServerID(room, server)
    }

    override fun getLastDeletionServerID(room: String, server: String): Long? =
        lokiAPIDatabase.getLastDeletionServerID(room, server)

    override fun setLastDeletionServerID(room: String, server: String, newValue: Long) {
        lokiAPIDatabase.setLastDeletionServerID(room, server, newValue)
    }

    override fun removeLastDeletionServerID(room: String, server: String) {
        lokiAPIDatabase.removeLastDeletionServerID(room, server)
    }

    override fun setUserCount(room: String, server: String, newValue: Int) {
        lokiAPIDatabase.setUserCount(room, server, newValue)
    }

    override fun setOpenGroupServerMessageID(messageID: Long, serverID: Long, threadID: Long, isSms: Boolean) {
        lokiMessageDatabase.setServerID(messageID, serverID, isSms)
        lokiMessageDatabase.setOriginalThreadID(messageID, serverID, threadID)
    }

    override fun getOpenGroup(room: String, server: String): OpenGroup? =
        getAllOpenGroups().values.firstOrNull { it.server == server && it.room == room }

    override fun setGroupMemberRoles(members: List<GroupMember>) {
        groupMemberDatabase.setGroupMembers(members)
    }

    override fun isDuplicateMessage(timestamp: Long): Boolean =
        getReceivedMessageTimestamps().contains(timestamp)

    override fun updateTitle(groupID: String, newValue: String) {
        groupDatabase.updateTitle(groupID, newValue)
    }

    override fun updateProfilePicture(groupID: String, newValue: ByteArray) {
        groupDatabase.updateProfilePicture(groupID, newValue)
    }

    override fun removeProfilePicture(groupID: String) {
        groupDatabase.removeProfilePicture(groupID)
    }

    override fun hasDownloadedProfilePicture(groupID: String): Boolean =
        groupDatabase.hasDownloadedProfilePicture(groupID)

    override fun getReceivedMessageTimestamps(): Set<Long> = SessionMetaProtocol.getTimestamps()

    override fun addReceivedMessageTimestamp(timestamp: Long) {
        SessionMetaProtocol.addTimestamp(timestamp)
    }

    override fun removeReceivedMessageTimestamps(timestamps: Set<Long>) {
        SessionMetaProtocol.removeTimestamps(timestamps)
    }

    override fun getMessageIdInDatabase(timestamp: Long, author: String): Long? =
        mmsSmsDatabase.getMessageFor(timestamp, fromSerialized(author))?.getId()

    override fun updateSentTimestamp(
        messageID: Long,
        isMms: Boolean,
        openGroupSentTimestamp: Long,
        threadId: Long
    ) {
        getMmsDatabaseElseSms(isMms).updateSentTimestamp(messageID, openGroupSentTimestamp, threadId)
    }

    override fun markAsSent(timestamp: Long, author: String) {
        mmsSmsDatabase.getMessageFor(timestamp, author)
            ?.let { getMmsDatabaseElseSms(it.isMms).markAsSent(it.id, true) }
    }

    override fun markAsSending(timestamp: Long, author: String) {
        mmsSmsDatabase.getMessageFor(timestamp, author)
            ?.let { getMmsDatabaseElseSms(it.isMms).markAsSending(it.id) }
    }

    override fun markUnidentified(timestamp: Long, author: String) {
        mmsSmsDatabase.getMessageFor(timestamp, author)
            ?.let { getMmsDatabaseElseSms(it.isMms).markUnidentified(it.id, true) }
    }

    override fun setErrorMessage(timestamp: Long, author: String, error: Exception) {
        val messageRecord = mmsSmsDatabase.getMessageFor(timestamp, author) ?: return
        getMmsDatabaseElseSms(messageRecord.isMms).markAsSentFailed(messageRecord.id)

        if (error.localizedMessage != null) {
            if (error is OnionRequestAPI.HTTPRequestFailedAtDestinationException && error.statusCode == 429) {
                "429: Rate limited."
            } else {
                error.localizedMessage!!
            }
        } else {
            error.javaClass.simpleName
        }.let { lokiMessageDatabase.setErrorMessage(messageRecord.id, it) }
    }

    override fun clearErrorMessage(messageID: Long) {
        lokiMessageDatabase.clearErrorMessage(messageID)
    }

    override fun setMessageServerHash(messageID: Long, serverHash: String) {
        lokiMessageDatabase.setMessageServerHash(messageID, serverHash)
    }

    override fun getGroup(groupID: String): GroupRecord? = groupDatabase.getGroup(groupID).orNull()

    override fun createGroup(groupId: String, title: String?, members: List<Address>, avatar: SignalServiceAttachmentPointer?, relay: String?, admins: List<Address>, formationTimestamp: Long) {
        groupDatabase.create(groupId, title, members, avatar, relay, admins, formationTimestamp)
    }

    override fun isGroupActive(groupPublicKey: String): Boolean =
        groupDatabase.getGroup(GroupUtil.doubleEncodeGroupID(groupPublicKey)).orNull()?.isActive == true

    override fun setActive(groupID: String, value: Boolean) {
        groupDatabase.setActive(groupID, value)
    }

    override fun getZombieMembers(groupID: String): Set<String> =
        groupDatabase.getGroupZombieMembers(groupID).map { it.address.serialize() }.toHashSet()

    override fun removeMember(groupID: String, member: Address) {
        groupDatabase.removeMember(groupID, member)
    }

    override fun updateMembers(groupID: String, members: List<Address>) {
        groupDatabase.updateMembers(groupID, members)
    }

    override fun setZombieMembers(groupID: String, members: List<Address>) {
        groupDatabase.updateZombieMembers(groupID, members)
    }

    override fun insertIncomingInfoMessage(context: Context, senderPublicKey: String, groupID: String, type: SignalServiceGroup.Type, name: String, members: Collection<String>, admins: Collection<String>, sentTimestamp: Long) {
        val group = SignalServiceGroup(type, GroupUtil.getDecodedGroupIDAsData(groupID), SignalServiceGroup.GroupType.SIGNAL, name, members.toList(), null, admins.toList())
        val m = IncomingTextMessage(fromSerialized(senderPublicKey), 1, sentTimestamp, "", Optional.of(group), 0, true, false)
        val updateData = UpdateMessageData.buildGroupUpdate(type, name, members)?.toJSON()
        val infoMessage = IncomingGroupMessage(m, groupID, updateData, true)
        smsDatabase.insertMessageInbox(infoMessage, true, true)
    }

    override fun insertOutgoingInfoMessage(context: Context, groupID: String, type: SignalServiceGroup.Type, name: String, members: Collection<String>, admins: Collection<String>, threadID: Long, sentTimestamp: Long) {
        val userPublicKey = getUserPublicKey()
        val recipient = Recipient.from(context, fromSerialized(groupID), false)

        val updateData = UpdateMessageData.buildGroupUpdate(type, name, members)?.toJSON() ?: ""
        val infoMessage = OutgoingGroupMediaMessage(recipient, updateData, groupID, null, sentTimestamp, 0, true, null, listOf(), listOf())
        if (mmsSmsDatabase.getMessageFor(sentTimestamp, userPublicKey) != null) return
        val infoMessageID = mmsDatabase.insertMessageOutbox(infoMessage, threadID, false, null, runThreadUpdate = true)
        mmsDatabase.markAsSent(infoMessageID, true)
    }

    override fun isClosedGroup(publicKey: String): Boolean {
        val isClosedGroup = lokiAPIDatabase.isClosedGroup(publicKey)
        val address = fromSerialized(publicKey)
        return address.isClosedGroup || isClosedGroup
    }

    override fun getClosedGroupEncryptionKeyPairs(groupPublicKey: String): MutableList<ECKeyPair> =
        lokiAPIDatabase.getClosedGroupEncryptionKeyPairs(groupPublicKey).toMutableList()

    override fun getLatestClosedGroupEncryptionKeyPair(groupPublicKey: String): ECKeyPair? =
        lokiAPIDatabase.getLatestClosedGroupEncryptionKeyPair(groupPublicKey)

    override fun getAllClosedGroupPublicKeys(): Set<String> =
        lokiAPIDatabase.getAllClosedGroupPublicKeys()

    override fun getAllActiveClosedGroupPublicKeys(): Set<String> =
        lokiAPIDatabase.getAllClosedGroupPublicKeys()
            .filter { getGroup(GroupUtil.doubleEncodeGroupID(it))?.isActive == true }
            .toSet()

    override fun addClosedGroupPublicKey(groupPublicKey: String) {
        lokiAPIDatabase.addClosedGroupPublicKey(groupPublicKey)
    }

    override fun removeClosedGroupPublicKey(groupPublicKey: String) {
        lokiAPIDatabase.removeClosedGroupPublicKey(groupPublicKey)
    }

    override fun addClosedGroupEncryptionKeyPair(encryptionKeyPair: ECKeyPair, groupPublicKey: String) {
        lokiAPIDatabase.addClosedGroupEncryptionKeyPair(encryptionKeyPair, groupPublicKey)
    }

    override fun removeAllClosedGroupEncryptionKeyPairs(groupPublicKey: String) {
        lokiAPIDatabase.removeAllClosedGroupEncryptionKeyPairs(groupPublicKey)
    }

    override fun updateFormationTimestamp(groupID: String, formationTimestamp: Long) {
        groupDatabase.updateFormationTimestamp(groupID, formationTimestamp)
    }

    override fun updateTimestampUpdated(groupID: String, updatedTimestamp: Long) {
        groupDatabase.updateTimestampUpdated(groupID, updatedTimestamp)
    }

    override fun setExpirationTimer(groupID: String, duration: Int) {
        Recipient.from(context, fromSerialized(groupID), false)
            .let { recipientDatabase.setExpireMessages(it, duration) }
    }

    override fun setServerCapabilities(server: String, capabilities: List<String>) =
        lokiAPIDatabase.setServerCapabilities(server, capabilities)

    override fun getServerCapabilities(server: String): List<String> =
        lokiAPIDatabase.getServerCapabilities(server)

    override fun getAllOpenGroups(): Map<Long, OpenGroup> =
        lokiThreadDatabase.getAllOpenGroups()

    override fun updateOpenGroup(openGroup: OpenGroup) {
        OpenGroupManager.updateOpenGroup(openGroup, context)
    }

    override fun getAllGroups(): List<GroupRecord> = groupDatabase.allGroups

    override fun addOpenGroup(urlAsString: String): OpenGroupApi.RoomInfo? =
        OpenGroupManager.addOpenGroup(urlAsString, context)

    override fun onOpenGroupAdded(server: String) {
        OpenGroupManager.restartPollerForServer(server.removeSuffix("/"))
    }

    override fun hasBackgroundGroupAddJob(groupJoinUrl: String): Boolean =
        sessionJobDatabase.hasBackgroundGroupAddJob(groupJoinUrl)

    override fun setProfileSharing(address: Address, value: Boolean) {
        Recipient.from(context, address, false)
            .let { recipientDatabase.setProfileSharing(it, value) }
    }

    override fun getOrCreateThreadIdFor(address: Address): Long =
        Recipient.from(context, address, false)
            .let(threadDatabase::getOrCreateThreadIdFor)

    override fun getOrCreateThreadIdFor(publicKey: String, groupPublicKey: String?, openGroupID: String?): Long {
        val key = openGroupID?.takeUnless(String::isEmpty)?.toByteArray()?.let(GroupUtil::getEncodedOpenGroupID)
            ?: groupPublicKey?.takeUnless(String::isEmpty)?.let(GroupUtil::doubleEncodeGroupID)
            ?: publicKey

        return fromSerialized(key)
            .let { Recipient.from(context, it, false) }
            .let(threadDatabase::getThreadIdIfExistsFor)
    }

    override fun getThreadId(publicKeyOrOpenGroupID: String): Long? =
        fromSerialized(publicKeyOrOpenGroupID).let(::getThreadId)

    override fun getThreadId(address: Address): Long? =
        Recipient.from(context, address, false).let(::getThreadId)

    override fun getThreadId(recipient: Recipient): Long? =
        threadDatabase.getThreadIdIfExistsFor(recipient).takeUnless { it < 0 }

    override fun getThreadIdForMms(mmsId: Long): Long =
        mmsDatabase.getMessage(mmsId).use { mmsDatabase.readerFor(it).next?.threadId ?: -1 }

    override fun getContactWithSessionID(sessionID: String): Contact? =
        sessionContactDatabase.getContactWithSessionID(sessionID)

    override fun getAllContacts(): Set<Contact> =
        sessionContactDatabase.getAllContacts()

    override fun setContact(contact: Contact) {
        sessionContactDatabase.setContact(contact)
    }

    override fun getRecipientForThread(threadId: Long): Recipient? =
        threadDatabase.getRecipientForThreadId(threadId)

    override fun getRecipientSettings(address: Address): Recipient.RecipientSettings? =
        recipientDatabase.getRecipientSettings(address).orNull()

    override fun addContacts(contacts: List<ConfigurationMessage.Contact>) {
        val moreContacts = contacts.filter { contact ->
            val id = SessionId(contact.publicKey)
            id.prefix != IdPrefix.BLINDED || blindedIdMappingDatabase.getBlindedIdMapping(contact.publicKey).none { it.sessionId != null }
        }
        for (contact in moreContacts) {
            val address = fromSerialized(contact.publicKey)
            val recipient = Recipient.from(context, address, true)
            if (!contact.profilePicture.isNullOrEmpty()) {
                recipientDatabase.setProfileAvatar(recipient, contact.profilePicture)
            }
            if (contact.profileKey?.isNotEmpty() == true) {
                recipientDatabase.setProfileKey(recipient, contact.profileKey)
            }
            if (contact.name.isNotEmpty()) {
                recipientDatabase.setProfileName(recipient, contact.name)
            }
            recipientDatabase.setProfileSharing(recipient, true)
            recipientDatabase.setRegistered(recipient, Recipient.RegisteredState.REGISTERED)
            // create Thread if needed
            val threadId = threadDatabase.getOrCreateThreadIdFor(recipient)
            if (contact.didApproveMe == true) {
                recipientDatabase.setApprovedMe(recipient, true)
            }
            if (contact.isApproved == true) {
                recipientDatabase.setApproved(recipient, true)
                threadDatabase.setHasSent(threadId, true)
            }
            if (contact.isBlocked == true) {
                recipientDatabase.setBlocked(recipient, true)
                threadDatabase.deleteConversation(threadId)
            }
        }
        if (contacts.isNotEmpty()) {
            threadDatabase.notifyConversationListListeners()
        }
    }

    override fun getLastUpdated(threadID: Long): Long = threadDatabase.getLastUpdated(threadID)

    override fun trimThread(threadID: Long, threadLimit: Int) {
        threadDatabase.trimThread(threadID, threadLimit)
    }

    override fun trimThreadBefore(threadID: Long, timestamp: Long) {
        threadDatabase.trimThreadBefore(threadID, timestamp)
    }

    override fun getMessageCount(threadID: Long): Long =
        mmsSmsDatabase.getConversationCount(threadID)



    override fun getAttachmentDataUri(attachmentId: AttachmentId): Uri =
        PartAuthority.getAttachmentDataUri(attachmentId)

    override fun getAttachmentThumbnailUri(attachmentId: AttachmentId): Uri =
        PartAuthority.getAttachmentThumbnailUri(attachmentId)

    override fun insertDataExtractionNotificationMessage(senderPublicKey: String, message: DataExtractionNotificationInfoMessage, sentTimestamp: Long) {
        val address = fromSerialized(senderPublicKey)
        val recipient = Recipient.from(context, address, false)

        if (recipient.isBlocked) return

        val mediaMessage = IncomingMediaMessage(
            address,
            sentTimestamp,
            -1,
            0,
            false,
            false,
            false,
            false,
            Optional.absent(),
            Optional.absent(),
            Optional.absent(),
            Optional.absent(),
            Optional.absent(),
            Optional.absent(),
            Optional.of(message)
        )

        mmsDatabase.insertSecureDecryptedMessageInbox(mediaMessage, -1, runIncrement = true, runThreadUpdate = true)
    }

    override fun insertMessageRequestResponse(response: MessageRequestResponse) {
        val userPublicKey = getUserPublicKey()
        val senderPublicKey = response.sender!!
        val recipientPublicKey = response.recipient!!
        if (userPublicKey == null || (userPublicKey != recipientPublicKey && userPublicKey != senderPublicKey)) return
        if (userPublicKey == senderPublicKey) {
            val requestRecipient = Recipient.from(context, fromSerialized(recipientPublicKey), false)
            recipientDatabase.setApproved(requestRecipient, true)
            val threadId = threadDatabase.getOrCreateThreadIdFor(requestRecipient)
            threadDatabase.setHasSent(threadId, true)
        } else {
            val sender = Recipient.from(context, fromSerialized(senderPublicKey), false)
            val threadId = threadDatabase.getOrCreateThreadIdFor(sender)
            val profile = response.profile
            if (profile != null) {
                val profileManager = SSKEnvironment.shared.profileManager
                val name = profile.displayName!!
                if (name.isNotEmpty()) {
                    profileManager.setName(context, sender, name)
                }
                val newProfileKey = profile.profileKey

                val needsProfilePicture = !AvatarHelper.avatarFileExists(context, sender.address)
                val profileKeyValid = newProfileKey?.isNotEmpty() == true && (newProfileKey.size == 16 || newProfileKey.size == 32) && profile.profilePictureURL?.isNotEmpty() == true
                val profileKeyChanged = (sender.profileKey == null || !MessageDigest.isEqual(sender.profileKey, newProfileKey))

                if ((profileKeyValid && profileKeyChanged) || (profileKeyValid && needsProfilePicture)) {
                    profileManager.setProfileKey(context, sender, newProfileKey!!)
                    profileManager.setUnidentifiedAccessMode(context, sender, Recipient.UnidentifiedAccessMode.UNKNOWN)
                    profileManager.setProfilePictureURL(context, sender, profile.profilePictureURL!!)
                }
            }
            threadDatabase.setHasSent(threadId, true)
            val mappings = mutableMapOf<String, BlindedIdMapping>()
            threadDatabase.readerFor(threadDatabase.conversationList).use { reader ->
                while (reader.next != null) {
                    val recipient = reader.current.recipient
                    val address = recipient.address.serialize()
                    val blindedId = when {
                        recipient.isGroupRecipient -> null
                        recipient.isOpenGroupInboxRecipient -> {
                            GroupUtil.getDecodedOpenGroupInbox(address)
                        }
                        else -> {
                            if (SessionId(address).prefix == IdPrefix.BLINDED) {
                                address
                            } else null
                        }
                    } ?: continue
                    blindedIdMappingDatabase.getBlindedIdMapping(blindedId).firstOrNull()?.let {
                        mappings[address] = it
                    }
                }
            }
            for (mapping in mappings) {
                if (!SodiumUtilities.sessionId(senderPublicKey, mapping.value.blindedId, mapping.value.serverId)) {
                    continue
                }
                blindedIdMappingDatabase.addBlindedIdMapping(mapping.value.copy(sessionId = senderPublicKey))

                val blindedThreadId = threadDatabase.getOrCreateThreadIdFor(Recipient.from(context, fromSerialized(mapping.key), false))
                mmsDatabase.updateThreadId(blindedThreadId, threadId)
                smsDatabase.updateThreadId(blindedThreadId, threadId)
                threadDatabase.deleteConversation(blindedThreadId)
            }
            recipientDatabase.setApproved(sender, true)
            recipientDatabase.setApprovedMe(sender, true)

            val message = IncomingMediaMessage(
                sender.address,
                response.sentTimestamp!!,
                -1,
                0,
                false,
                false,
                true,
                false,
                Optional.absent(),
                Optional.absent(),
                Optional.absent(),
                Optional.absent(),
                Optional.absent(),
                Optional.absent(),
                Optional.absent()
            )
            mmsDatabase.insertSecureDecryptedMessageInbox(message, threadId, runIncrement = true, runThreadUpdate = true)
        }
    }

    override fun setRecipientApproved(recipient: Recipient, approved: Boolean) {
        recipientDatabase.setApproved(recipient, approved)
    }

    override fun setRecipientApprovedMe(recipient: Recipient, approvedMe: Boolean) {
        recipientDatabase.setApprovedMe(recipient, approvedMe)
    }

    override fun insertCallMessage(senderPublicKey: String, callMessageType: CallMessageType, sentTimestamp: Long) {
        val address = fromSerialized(senderPublicKey)
        val callMessage = IncomingTextMessage.fromCallInfo(callMessageType, address, Optional.absent(), sentTimestamp)
        smsDatabase.insertCallMessage(callMessage)
    }

    override fun conversationHasOutgoing(userPublicKey: String): Boolean =
        threadDatabase.getThreadIdIfExistsFor(userPublicKey)
            .takeUnless { it == -1L }
            ?.let(threadDatabase::getLastSeenAndHasSent)
            ?.second()
            ?: false

    override fun getLastInboxMessageId(server: String): Long? =
        lokiAPIDatabase.getLastInboxMessageId(server)

    override fun setLastInboxMessageId(server: String, messageId: Long) {
        lokiAPIDatabase.setLastInboxMessageId(server, messageId)
    }

    override fun removeLastInboxMessageId(server: String) {
        lokiAPIDatabase.removeLastInboxMessageId(server)
    }

    override fun getLastOutboxMessageId(server: String): Long? =
        lokiAPIDatabase.getLastOutboxMessageId(server)

    override fun setLastOutboxMessageId(server: String, messageId: Long) {
        lokiAPIDatabase.setLastOutboxMessageId(server, messageId)
    }

    override fun removeLastOutboxMessageId(server: String) {
        lokiAPIDatabase.removeLastOutboxMessageId(server)
    }

    override fun getOrCreateBlindedIdMapping(
        blindedId: String,
        server: String,
        serverPublicKey: String,
        fromOutbox: Boolean
    ): BlindedIdMapping {
        val mapping = blindedIdMappingDatabase.getBlindedIdMapping(blindedId).firstOrNull() ?: BlindedIdMapping(blindedId, null, server, serverPublicKey)
        if (mapping.sessionId != null) {
            return mapping
        }
        threadDatabase.readerFor(threadDatabase.conversationList).use { reader ->
            while (reader.next != null) {
                val recipient = reader.current.recipient
                val sessionId = recipient.address.serialize()
                if (!recipient.isGroupRecipient && SodiumUtilities.sessionId(sessionId, blindedId, serverPublicKey)) {
                    val contactMapping = mapping.copy(sessionId = sessionId)
                    blindedIdMappingDatabase.addBlindedIdMapping(contactMapping)
                    return contactMapping
                }
            }
        }
        blindedIdMappingDatabase.getBlindedIdMappingsExceptFor(server).forEach {
            if (SodiumUtilities.sessionId(it.sessionId!!, blindedId, serverPublicKey)) {
                val otherMapping = mapping.copy(sessionId = it.sessionId)
                blindedIdMappingDatabase.addBlindedIdMapping(otherMapping)
                return otherMapping
            }
        }
        blindedIdMappingDatabase.addBlindedIdMapping(mapping)
        return mapping
    }

    override fun addReaction(reaction: Reaction, messageSender: String, notifyUnread: Boolean) {
        val timestamp = reaction.timestamp
        val localId = reaction.localId
        val isMms = reaction.isMms
        val messageId = if (localId != null && localId > 0 && isMms != null) {
            MessageId(localId, isMms)
        } else if (timestamp != null && timestamp > 0) {
            val messageRecord = mmsSmsDatabase.getMessageForTimestamp(timestamp) ?: return
            MessageId(messageRecord.id, messageRecord.isMms)
        } else return
        reactionDatabase.addReaction(
            messageId,
            ReactionRecord(
                messageId = messageId.id,
                isMms = messageId.mms,
                author = messageSender,
                emoji = reaction.emoji!!,
                serverId = reaction.serverId!!,
                count = reaction.count!!,
                sortId = reaction.index!!,
                dateSent = reaction.dateSent!!,
                dateReceived = reaction.dateReceived!!
            ),
            notifyUnread
        )
    }

    override fun removeReaction(emoji: String, messageTimestamp: Long, author: String, notifyUnread: Boolean) {
        val messageRecord = mmsSmsDatabase.getMessageForTimestamp(messageTimestamp) ?: return
        val messageId = MessageId(messageRecord.id, messageRecord.isMms)
        reactionDatabase.deleteReaction(emoji, messageId, author, notifyUnread)
    }

    override fun updateReactionIfNeeded(message: Message, sender: String, openGroupSentTimestamp: Long) {
        var reaction = reactionDatabase.getReactionFor(message.sentTimestamp!!, sender) ?: return
        if (openGroupSentTimestamp != -1L) {
            addReceivedMessageTimestamp(openGroupSentTimestamp)
            reaction = reaction.copy(dateSent = openGroupSentTimestamp)
        }
        message.serverHash?.let {
            reaction = reaction.copy(serverId = it)
        }
        message.openGroupServerMessageID?.let {
            reaction = reaction.copy(serverId = "$it")
        }
        reactionDatabase.updateReaction(reaction)
    }

    override fun deleteReactions(messageId: Long, mms: Boolean) {
        reactionDatabase.deleteMessageReactions(MessageId(messageId, mms))
    }

    override fun unblock(toUnblock: List<Recipient>) {
        recipientDatabase.setBlocked(toUnblock, false)
    }

    override fun blockedContacts(): List<Recipient> = recipientDatabase.blockedContacts

}