package org.thoughtcrime.securesms.database

import android.content.Context
import android.net.Uri
import network.loki.messenger.libsession_util.*
import network.loki.messenger.libsession_util.util.Conversation
import network.loki.messenger.libsession_util.util.UserPic
import org.session.libsession.avatars.AvatarHelper
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.BlindedIdMapping
import org.session.libsession.messaging.calls.CallMessageType
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.jobs.*
import org.session.libsession.messaging.messages.Destination
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
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.crypto.ecc.ECKeyPair
import org.session.libsignal.messages.SignalServiceAttachmentPointer
import org.session.libsignal.messages.SignalServiceGroup
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.KeyHelper
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.guava.Optional
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import org.thoughtcrime.securesms.util.SessionMetaProtocol
import java.security.MessageDigest
import network.loki.messenger.libsession_util.util.Contact as LibSessionContact

class Storage(context: Context, helper: SQLCipherOpenHelper, private val configFactory: ConfigFactory) : Database(context, helper), StorageProtocol {
    
    override fun getUserPublicKey(): String? {
        return TextSecurePreferences.getLocalNumber(context)
    }

    override fun getUserX25519KeyPair(): ECKeyPair {
        return DatabaseComponent.get(context).lokiAPIDatabase().getUserX25519KeyPair()
    }

    override fun getUserProfile(): Profile {
        val displayName = TextSecurePreferences.getProfileName(context)!!
        val profileKey = ProfileKeyUtil.getProfileKey(context)
        val profilePictureUrl = TextSecurePreferences.getProfilePictureURL(context)
        return Profile(displayName, profileKey, profilePictureUrl)
    }

    override fun setUserProfilePictureURL(newProfilePicture: String?) {
        val ourRecipient = fromSerialized(getUserPublicKey()!!).let {
            Recipient.from(context, it, false)
        }
        TextSecurePreferences.setProfilePictureURL(context, newProfilePicture)
        ApplicationContext.getInstance(context).jobManager.add(RetrieveProfileAvatarJob(ourRecipient, newProfilePicture))
    }

    override fun getOrGenerateRegistrationID(): Int {
        var registrationID = TextSecurePreferences.getLocalRegistrationId(context)
        if (registrationID == 0) {
            registrationID = KeyHelper.generateRegistrationId(false)
            TextSecurePreferences.setLocalRegistrationId(context, registrationID)
        }
        return registrationID
    }

    override fun persistAttachments(messageID: Long, attachments: List<Attachment>): List<Long> {
        val database = DatabaseComponent.get(context).attachmentDatabase()
        val databaseAttachments = attachments.mapNotNull { it.toSignalAttachment() }
        return database.insertAttachments(messageID, databaseAttachments)
    }

    override fun getAttachmentsForMessage(messageID: Long): List<DatabaseAttachment> {
        val database = DatabaseComponent.get(context).attachmentDatabase()
        return database.getAttachmentsForMessage(messageID)
    }

    override fun getLastSeen(threadId: Long): Long {
        val threadDb = DatabaseComponent.get(context).threadDatabase()
        return threadDb.getLastSeenAndHasSent(threadId)?.first() ?: 0L
    }

    override fun markConversationAsRead(threadId: Long, lastSeenTime: Long) {
        val threadDb = DatabaseComponent.get(context).threadDatabase()
        getRecipientForThread(threadId)?.let { recipient ->
            threadDb.markAllAsRead(threadId, recipient.isGroupRecipient, lastSeenTime)
            configFactory.convoVolatile?.let { config ->
                val convo = when {
                    // recipient closed group
                    recipient.isClosedGroupRecipient -> config.getOrConstructLegacyGroup(GroupUtil.doubleDecodeGroupId(recipient.address.serialize()))
                    // recipient is open group
                    recipient.isOpenGroupRecipient -> {
                        val openGroupJoinUrl = getOpenGroup(threadId)?.joinURL ?: return
                        Conversation.Community.parseFullUrl(openGroupJoinUrl)?.let { (base, room, pubKey) ->
                            config.getOrConstructCommunity(base, room, pubKey)
                        } ?: return
                    }
                    // otherwise recipient is one to one
                    recipient.isContactRecipient -> config.getOrConstructOneToOne(recipient.address.serialize())
                    else -> throw NullPointerException("Weren't expecting to have a convo with address ${recipient.address.serialize()}")
                }
                convo.lastRead = lastSeenTime
                config.set(convo)
                ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(context)
            }
        }
    }

    override fun updateThread(threadId: Long, unarchive: Boolean) {
        val threadDb = DatabaseComponent.get(context).threadDatabase()
        threadDb.update(threadId, unarchive)
    }

    override fun persist(message: VisibleMessage,
                         quotes: QuoteModel?,
                         linkPreview: List<LinkPreview?>,
                         groupPublicKey: String?,
                         openGroupID: String?,
                         attachments: List<Attachment>,
                         runThreadUpdate: Boolean): Long? {
        var messageID: Long? = null
        val senderAddress = fromSerialized(message.sender!!)
        val isUserSender = (message.sender!! == getUserPublicKey())
        val isUserBlindedSender = message.threadID?.takeIf { it >= 0 }?.let { getOpenGroup(it)?.publicKey }
            ?.let { SodiumUtilities.sessionId(getUserPublicKey()!!, message.sender!!, it) } ?: false
        val group: Optional<SignalServiceGroup> = when {
            openGroupID != null -> Optional.of(SignalServiceGroup(openGroupID.toByteArray(), SignalServiceGroup.GroupType.PUBLIC_CHAT))
            groupPublicKey != null -> {
                val doubleEncoded = GroupUtil.doubleEncodeGroupID(groupPublicKey)
                Optional.of(SignalServiceGroup(GroupUtil.getDecodedGroupIDAsData(doubleEncoded), SignalServiceGroup.GroupType.SIGNAL))
            }
            else -> Optional.absent()
        }
        val pointers = attachments.mapNotNull {
            it.toSignalAttachment()
        }
        val targetAddress = if ((isUserSender || isUserBlindedSender) && !message.syncTarget.isNullOrEmpty()) {
            fromSerialized(message.syncTarget!!)
        } else if (group.isPresent) {
            fromSerialized(GroupUtil.getEncodedId(group.get()))
        } else {
            senderAddress
        }
        val targetRecipient = Recipient.from(context, targetAddress, false)
        if (!targetRecipient.isGroupRecipient) {
            val recipientDb = DatabaseComponent.get(context).recipientDatabase()
            if (isUserSender || isUserBlindedSender) {
                setRecipientApproved(targetRecipient, true)
            } else {
                setRecipientApprovedMe(targetRecipient, true)
            }
        }
        if (message.isMediaMessage() || attachments.isNotEmpty()) {
            val quote: Optional<QuoteModel> = if (quotes != null) Optional.of(quotes) else Optional.absent()
            val linkPreviews: Optional<List<LinkPreview>> = if (linkPreview.isEmpty()) Optional.absent() else Optional.of(linkPreview.mapNotNull { it!! })
            val mmsDatabase = DatabaseComponent.get(context).mmsDatabase()
            val insertResult = if (isUserSender || isUserBlindedSender) {
                val mediaMessage = OutgoingMediaMessage.from(message, targetRecipient, pointers, quote.orNull(), linkPreviews.orNull()?.firstOrNull())
                mmsDatabase.insertSecureDecryptedMessageOutbox(mediaMessage, message.threadID ?: -1, message.sentTimestamp!!, runThreadUpdate)
            } else {
                // It seems like we have replaced SignalServiceAttachment with SessionServiceAttachment
                val signalServiceAttachments = attachments.mapNotNull {
                    it.toSignalPointer()
                }
                val mediaMessage = IncomingMediaMessage.from(message, senderAddress, targetRecipient.expireMessages * 1000L, group, signalServiceAttachments, quote, linkPreviews)
                mmsDatabase.insertSecureDecryptedMessageInbox(mediaMessage, message.threadID ?: -1, message.receivedTimestamp ?: 0, runThreadUpdate)
            }
            if (insertResult.isPresent) {
                messageID = insertResult.get().messageId
            }
        } else {
            val smsDatabase = DatabaseComponent.get(context).smsDatabase()
            val isOpenGroupInvitation = (message.openGroupInvitation != null)

            val insertResult = if (isUserSender || isUserBlindedSender) {
                val textMessage = if (isOpenGroupInvitation) OutgoingTextMessage.fromOpenGroupInvitation(message.openGroupInvitation, targetRecipient, message.sentTimestamp)
                else OutgoingTextMessage.from(message, targetRecipient)
                smsDatabase.insertMessageOutbox(message.threadID ?: -1, textMessage, message.sentTimestamp!!, runThreadUpdate)
            } else {
                val textMessage = if (isOpenGroupInvitation) IncomingTextMessage.fromOpenGroupInvitation(message.openGroupInvitation, senderAddress, message.sentTimestamp)
                else IncomingTextMessage.from(message, senderAddress, group, targetRecipient.expireMessages * 1000L)
                val encrypted = IncomingEncryptedMessage(textMessage, textMessage.messageBody)
                smsDatabase.insertMessageInbox(encrypted, message.receivedTimestamp ?: 0, runThreadUpdate)
            }
            insertResult.orNull()?.let { result ->
                messageID = result.messageId
            }
        }
        message.serverHash?.let { serverHash ->
            messageID?.let { id ->
                DatabaseComponent.get(context).lokiMessageDatabase().setMessageServerHash(id, serverHash)
            }
        }
        return messageID
    }

    override fun persistJob(job: Job) {
        DatabaseComponent.get(context).sessionJobDatabase().persistJob(job)
    }

    override fun markJobAsSucceeded(jobId: String) {
        DatabaseComponent.get(context).sessionJobDatabase().markJobAsSucceeded(jobId)
    }

    override fun markJobAsFailedPermanently(jobId: String) {
        DatabaseComponent.get(context).sessionJobDatabase().markJobAsFailedPermanently(jobId)
    }

    override fun getAllPendingJobs(type: String): Map<String, Job?> {
        return DatabaseComponent.get(context).sessionJobDatabase().getAllPendingJobs(type)
    }

    override fun getAttachmentUploadJob(attachmentID: Long): AttachmentUploadJob? {
        return DatabaseComponent.get(context).sessionJobDatabase().getAttachmentUploadJob(attachmentID)
    }

    override fun getMessageSendJob(messageSendJobID: String): MessageSendJob? {
        return DatabaseComponent.get(context).sessionJobDatabase().getMessageSendJob(messageSendJobID)
    }

    override fun getMessageReceiveJob(messageReceiveJobID: String): MessageReceiveJob? {
        return DatabaseComponent.get(context).sessionJobDatabase().getMessageReceiveJob(messageReceiveJobID)
    }

    override fun getGroupAvatarDownloadJob(server: String, room: String, imageId: String?): GroupAvatarDownloadJob? {
        return DatabaseComponent.get(context).sessionJobDatabase().getGroupAvatarDownloadJob(server, room, imageId)
    }

    override fun getConfigSyncJob(destination: Destination): Job? {
        return DatabaseComponent.get(context).sessionJobDatabase().getAllPendingJobs(ConfigurationSyncJob.KEY).values.firstOrNull {
            (it as? ConfigurationSyncJob)?.destination == destination
        }
    }

    override fun resumeMessageSendJobIfNeeded(messageSendJobID: String) {
        val job = DatabaseComponent.get(context).sessionJobDatabase().getMessageSendJob(messageSendJobID) ?: return
        JobQueue.shared.resumePendingSendMessage(job)
    }

    override fun isJobCanceled(job: Job): Boolean {
        return DatabaseComponent.get(context).sessionJobDatabase().isJobCanceled(job)
    }

    override fun getAuthToken(room: String, server: String): String? {
        val id = "$server.$room"
        return DatabaseComponent.get(context).lokiAPIDatabase().getAuthToken(id)
    }

    override fun notifyConfigUpdates(forConfigObject: ConfigBase) {
        notifyUpdates(forConfigObject)
    }

    fun notifyUpdates(forConfigObject: ConfigBase) {
        when (forConfigObject) {
            is UserProfile -> updateUser(forConfigObject)
            is Contacts -> updateContacts(forConfigObject)
            is ConversationVolatileConfig -> updateConvoVolatile(forConfigObject)
            is UserGroupsConfig -> updateUserGroups(forConfigObject)
        }
    }

    private fun updateUser(userProfile: UserProfile) {
        val userPublicKey = getUserPublicKey() ?: return
        // would love to get rid of recipient and context from this
        val recipient = Recipient.from(context, fromSerialized(userPublicKey), false)
        // update name
        val name = userProfile.getName() ?: return
        val userPic = userProfile.getPic()
        val profileManager = SSKEnvironment.shared.profileManager
        if (name.isNotEmpty()) {
            TextSecurePreferences.setProfileName(context, name)
            profileManager.setName(context, recipient, name)
        }

        // update pfp
        if (userPic == UserPic.DEFAULT) {
            // clear picture if userPic is null
            TextSecurePreferences.setProfileKey(context, null)
            ProfileKeyUtil.setEncodedProfileKey(context, null)
            profileManager.setProfileKey(context, recipient, null)
            setUserProfilePictureURL(null)
        } else if (userPic.key.isNotEmpty() && userPic.url.isNotEmpty()
            && TextSecurePreferences.getProfilePictureURL(context) != userPic.url) {
            val profileKey = Base64.encodeBytes(userPic.key)
            ProfileKeyUtil.setEncodedProfileKey(context, profileKey)
            profileManager.setProfileKey(context, recipient, userPic.key)
            setUserProfilePictureURL(userPic.url)
        }
    }

    private fun updateContacts(contacts: Contacts) {
        val extracted = contacts.all().toList()
        val profileManager = SSKEnvironment.shared.profileManager
        extracted.forEach { contact ->
            val address = fromSerialized(contact.id)
            val recipient = Recipient.from(context, address, false)
            setBlocked(listOf(recipient), contact.blocked, fromConfigUpdate = true)
            setRecipientApproved(recipient, contact.approved)
            setRecipientApprovedMe(recipient, contact.approvedMe)
            profileManager.setName(context, recipient, contact.name)
            profileManager.setNickname(context, recipient, contact.nickname)
            if (contact.profilePicture != UserPic.DEFAULT) {
                val (url, key) = contact.profilePicture
                if (key.size != ProfileKeyUtil.PROFILE_KEY_BYTES) return@forEach
                profileManager.setProfileKey(context, recipient, key)
                profileManager.setUnidentifiedAccessMode(context, recipient, Recipient.UnidentifiedAccessMode.UNKNOWN)
                profileManager.setProfilePictureURL(context, recipient, url)
            }
            Log.d("Loki-DBG", "Updated contact $contact")
        }
    }

    private fun updateConvoVolatile(convos: ConversationVolatileConfig) {
        val extracted = convos.all()
        for (conversation in extracted) {
            val threadId = when (conversation) {
                is Conversation.OneToOne -> conversation.sessionId.let {
                    getOrCreateThreadIdFor(fromSerialized(it))
                }
                is Conversation.LegacyGroup -> conversation.groupId.let {
                    getOrCreateThreadIdFor("", it,null)
                }
                is Conversation.Community -> conversation.baseCommunityInfo.baseUrl.let {
                    getOrCreateThreadIdFor("",null, it)
                }
            }
            if (threadId >= 0) {
                markConversationAsRead(threadId, conversation.lastRead)
                updateThread(threadId, false)
            }
        }
    }

    private fun updateUserGroups(userGroups: UserGroupsConfig) {
        val threadDb = DatabaseComponent.get(context).threadDatabase()
        val communities = userGroups.allCommunityInfo()
        val lgc = userGroups.allLegacyGroupInfo()
        val allOpenGroups = getAllOpenGroups()
        val toDeleteCommunities = allOpenGroups.filter { it.value.joinURL !in communities.map { it.community.fullUrl() } }
        val existingOpenGroups: Map<Long, OpenGroup> = allOpenGroups.filterKeys { it !in toDeleteCommunities.keys }
        val existingJoinUrls = existingOpenGroups.values.map { it.joinURL }
        val existingClosedGroups = getAllGroups()
        val lgcIds = lgc.map { it.sessionId }
        val toDeleteClosedGroups = existingClosedGroups.filter { group ->
            GroupUtil.doubleDecodeGroupId(group.encodedId) !in lgcIds
        }

        // delete the ones which are not listed in the config
        toDeleteCommunities.values.forEach { openGroup ->
            OpenGroupManager.delete(openGroup.server, openGroup.room, context)
        }

//        GroupManager.deleteGroup()

        for (groupInfo in communities) {
            val groupBaseCommunity = groupInfo.community
            if (groupBaseCommunity.fullUrl() !in existingJoinUrls) {
                // add it
                val (threadId, _) = OpenGroupManager.add(groupBaseCommunity.baseUrl, groupBaseCommunity.room, groupBaseCommunity.pubKeyHex, context)
                threadDb.setPinned(threadId, groupInfo.priority >= 1)
            } else {
                val (threadId, _) = existingOpenGroups.entries.first { (_, v) -> v.joinURL == groupInfo.community.fullUrl() }
                threadDb.setPinned(threadId, groupInfo.priority >= 1)
            }
        }

        for (group in lgc) {
            val existingGroup = existingClosedGroups.firstOrNull { GroupUtil.doubleDecodeGroupId(it.encodedId) == group.sessionId }
            val existingThread = existingGroup?.let { getThreadId(existingGroup.encodedId) }
            if (existingGroup != null) {
                Log.d("Loki-DBG", "Existing closed group, don't add")
                if (group.hidden && existingThread != null) {
                    threadDb.setThreadArchived(existingThread)
                } else if (existingThread == null) {
                    Log.w("Loki-DBG", "Existing group had no thread to hide")
                }
            } else {
                val members = group.members.keys.map { Address.fromSerialized(it) }
                val admins = group.members.filter { it.value /*admin = true*/ }.keys.map { Address.fromSerialized(it) }
                val groupId = GroupUtil.doubleEncodeGroupID(group.sessionId)
                val title = group.name
                val formationTimestamp = 0L
                createGroup(groupId, title, members, null, null, admins, formationTimestamp)
            }
        }
    }

    override fun setAuthToken(room: String, server: String, newValue: String) {
        val id = "$server.$room"
        DatabaseComponent.get(context).lokiAPIDatabase().setAuthToken(id, newValue)
    }

    override fun removeAuthToken(room: String, server: String) {
        val id = "$server.$room"
        DatabaseComponent.get(context).lokiAPIDatabase().setAuthToken(id, null)
    }

    override fun getOpenGroup(threadId: Long): OpenGroup? {
        if (threadId.toInt() < 0) { return null }
        val database = databaseHelper.readableDatabase
        return database.get(LokiThreadDatabase.publicChatTable, "${LokiThreadDatabase.threadID} = ?", arrayOf( threadId.toString() )) { cursor ->
            val publicChatAsJson = cursor.getString(LokiThreadDatabase.publicChat)
            OpenGroup.fromJSON(publicChatAsJson)
        }
    }

    override fun getOpenGroupPublicKey(server: String): String? {
        return DatabaseComponent.get(context).lokiAPIDatabase().getOpenGroupPublicKey(server)
    }

    override fun setOpenGroupPublicKey(server: String, newValue: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().setOpenGroupPublicKey(server, newValue)
    }

    override fun getLastMessageServerID(room: String, server: String): Long? {
        return DatabaseComponent.get(context).lokiAPIDatabase().getLastMessageServerID(room, server)
    }

    override fun setLastMessageServerID(room: String, server: String, newValue: Long) {
        DatabaseComponent.get(context).lokiAPIDatabase().setLastMessageServerID(room, server, newValue)
    }

    override fun removeLastMessageServerID(room: String, server: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().removeLastMessageServerID(room, server)
    }

    override fun getLastDeletionServerID(room: String, server: String): Long? {
        return DatabaseComponent.get(context).lokiAPIDatabase().getLastDeletionServerID(room, server)
    }

    override fun setLastDeletionServerID(room: String, server: String, newValue: Long) {
        DatabaseComponent.get(context).lokiAPIDatabase().setLastDeletionServerID(room, server, newValue)
    }

    override fun removeLastDeletionServerID(room: String, server: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().removeLastDeletionServerID(room, server)
    }

    override fun setUserCount(room: String, server: String, newValue: Int) {
        DatabaseComponent.get(context).lokiAPIDatabase().setUserCount(room, server, newValue)
    }

    override fun setOpenGroupServerMessageID(messageID: Long, serverID: Long, threadID: Long, isSms: Boolean) {
        DatabaseComponent.get(context).lokiMessageDatabase().setServerID(messageID, serverID, isSms)
        DatabaseComponent.get(context).lokiMessageDatabase().setOriginalThreadID(messageID, serverID, threadID)
    }

    override fun getOpenGroup(room: String, server: String): OpenGroup? {
        return getAllOpenGroups().values.firstOrNull { it.server == server && it.room == room }
    }

    override fun setGroupMemberRoles(members: List<GroupMember>) {
        DatabaseComponent.get(context).groupMemberDatabase().setGroupMembers(members)
    }

    override fun isDuplicateMessage(timestamp: Long): Boolean {
        return getReceivedMessageTimestamps().contains(timestamp)
    }

    override fun updateTitle(groupID: String, newValue: String) {
        DatabaseComponent.get(context).groupDatabase().updateTitle(groupID, newValue)
    }

    override fun updateProfilePicture(groupID: String, newValue: ByteArray) {
        DatabaseComponent.get(context).groupDatabase().updateProfilePicture(groupID, newValue)
    }

    override fun removeProfilePicture(groupID: String) {
        DatabaseComponent.get(context).groupDatabase().removeProfilePicture(groupID)
    }

    override fun hasDownloadedProfilePicture(groupID: String): Boolean {
        return DatabaseComponent.get(context).groupDatabase().hasDownloadedProfilePicture(groupID)
    }

    override fun getReceivedMessageTimestamps(): Set<Long> {
        return SessionMetaProtocol.getTimestamps()
    }

    override fun addReceivedMessageTimestamp(timestamp: Long) {
        SessionMetaProtocol.addTimestamp(timestamp)
    }

    override fun removeReceivedMessageTimestamps(timestamps: Set<Long>) {
        SessionMetaProtocol.removeTimestamps(timestamps)
    }

    override fun getMessageIdInDatabase(timestamp: Long, author: String): Long? {
        val database = DatabaseComponent.get(context).mmsSmsDatabase()
        val address = fromSerialized(author)
        return database.getMessageFor(timestamp, address)?.getId()
    }

    override fun updateSentTimestamp(
        messageID: Long,
        isMms: Boolean,
        openGroupSentTimestamp: Long,
        threadId: Long
    ) {
        if (isMms) {
            val mmsDb = DatabaseComponent.get(context).mmsDatabase()
            mmsDb.updateSentTimestamp(messageID, openGroupSentTimestamp, threadId)
        } else {
            val smsDb = DatabaseComponent.get(context).smsDatabase()
            smsDb.updateSentTimestamp(messageID, openGroupSentTimestamp, threadId)
        }
    }

    override fun markAsSent(timestamp: Long, author: String) {
        val database = DatabaseComponent.get(context).mmsSmsDatabase()
        val messageRecord = database.getMessageFor(timestamp, author) ?: return
        if (messageRecord.isMms) {
            val mmsDatabase = DatabaseComponent.get(context).mmsDatabase()
            mmsDatabase.markAsSent(messageRecord.getId(), true)
        } else {
            val smsDatabase = DatabaseComponent.get(context).smsDatabase()
            smsDatabase.markAsSent(messageRecord.getId(), true)
        }
    }

    override fun markAsSending(timestamp: Long, author: String) {
        val database = DatabaseComponent.get(context).mmsSmsDatabase()
        val messageRecord = database.getMessageFor(timestamp, author) ?: return
        if (messageRecord.isMms) {
            val mmsDatabase = DatabaseComponent.get(context).mmsDatabase()
            mmsDatabase.markAsSending(messageRecord.getId())
        } else {
            val smsDatabase = DatabaseComponent.get(context).smsDatabase()
            smsDatabase.markAsSending(messageRecord.getId())
            messageRecord.isPending
        }
    }

    override fun markUnidentified(timestamp: Long, author: String) {
        val database = DatabaseComponent.get(context).mmsSmsDatabase()
        val messageRecord = database.getMessageFor(timestamp, author) ?: return
        if (messageRecord.isMms) {
            val mmsDatabase = DatabaseComponent.get(context).mmsDatabase()
            mmsDatabase.markUnidentified(messageRecord.getId(), true)
        } else {
            val smsDatabase = DatabaseComponent.get(context).smsDatabase()
            smsDatabase.markUnidentified(messageRecord.getId(), true)
        }
    }

    override fun setErrorMessage(timestamp: Long, author: String, error: Exception) {
        val database = DatabaseComponent.get(context).mmsSmsDatabase()
        val messageRecord = database.getMessageFor(timestamp, author) ?: return
        if (messageRecord.isMms) {
            val mmsDatabase = DatabaseComponent.get(context).mmsDatabase()
            mmsDatabase.markAsSentFailed(messageRecord.getId())
        } else {
            val smsDatabase = DatabaseComponent.get(context).smsDatabase()
            smsDatabase.markAsSentFailed(messageRecord.getId())
        }
        if (error.localizedMessage != null) {
            val message: String
            if (error is OnionRequestAPI.HTTPRequestFailedAtDestinationException && error.statusCode == 429) {
                message = "429: Rate limited."
            } else {
                message = error.localizedMessage!!
            }
            DatabaseComponent.get(context).lokiMessageDatabase().setErrorMessage(messageRecord.getId(), message)
        } else {
            DatabaseComponent.get(context).lokiMessageDatabase().setErrorMessage(messageRecord.getId(), error.javaClass.simpleName)
        }
    }

    override fun clearErrorMessage(messageID: Long) {
        val db = DatabaseComponent.get(context).lokiMessageDatabase()
        db.clearErrorMessage(messageID)
    }

    override fun setMessageServerHash(messageID: Long, serverHash: String) {
        DatabaseComponent.get(context).lokiMessageDatabase().setMessageServerHash(messageID, serverHash)
    }

    override fun getGroup(groupID: String): GroupRecord? {
        val group = DatabaseComponent.get(context).groupDatabase().getGroup(groupID)
        return if (group.isPresent) { group.get() } else null
    }

    override fun createGroup(groupId: String, title: String?, members: List<Address>, avatar: SignalServiceAttachmentPointer?, relay: String?, admins: List<Address>, formationTimestamp: Long) {
        DatabaseComponent.get(context).groupDatabase().create(groupId, title, members, avatar, relay, admins, formationTimestamp)
        val volatiles = configFactory.convoVolatile ?: return
        val groupPublicKey = GroupUtil.doubleDecodeGroupId(groupId)
        val groupVolatileConfig = volatiles.getOrConstructLegacyGroup(groupPublicKey)
        groupVolatileConfig.lastRead = formationTimestamp
        volatiles.set(groupVolatileConfig)
        ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(context)
    }

    override fun isGroupActive(groupPublicKey: String): Boolean {
        return DatabaseComponent.get(context).groupDatabase().getGroup(GroupUtil.doubleEncodeGroupID(groupPublicKey)).orNull()?.isActive == true
    }

    override fun setActive(groupID: String, value: Boolean) {
        DatabaseComponent.get(context).groupDatabase().setActive(groupID, value)
    }

    override fun getZombieMembers(groupID: String): Set<String> {
        return DatabaseComponent.get(context).groupDatabase().getGroupZombieMembers(groupID).map { it.address.serialize() }.toHashSet()
    }

    override fun removeMember(groupID: String, member: Address) {
        DatabaseComponent.get(context).groupDatabase().removeMember(groupID, member)
    }

    override fun updateMembers(groupID: String, members: List<Address>) {
        DatabaseComponent.get(context).groupDatabase().updateMembers(groupID, members)
    }

    override fun setZombieMembers(groupID: String, members: List<Address>) {
        DatabaseComponent.get(context).groupDatabase().updateZombieMembers(groupID, members)
    }

    override fun insertIncomingInfoMessage(context: Context, senderPublicKey: String, groupID: String, type: SignalServiceGroup.Type, name: String, members: Collection<String>, admins: Collection<String>, sentTimestamp: Long) {
        val group = SignalServiceGroup(type, GroupUtil.getDecodedGroupIDAsData(groupID), SignalServiceGroup.GroupType.SIGNAL, name, members.toList(), null, admins.toList())
        val m = IncomingTextMessage(fromSerialized(senderPublicKey), 1, sentTimestamp, "", Optional.of(group), 0, true, false)
        val updateData = UpdateMessageData.buildGroupUpdate(type, name, members)?.toJSON()
        val infoMessage = IncomingGroupMessage(m, groupID, updateData, true)
        val smsDB = DatabaseComponent.get(context).smsDatabase()
        smsDB.insertMessageInbox(infoMessage,  true)
    }

    override fun insertOutgoingInfoMessage(context: Context, groupID: String, type: SignalServiceGroup.Type, name: String, members: Collection<String>, admins: Collection<String>, threadID: Long, sentTimestamp: Long) {
        val userPublicKey = getUserPublicKey()
        val recipient = Recipient.from(context, fromSerialized(groupID), false)

        val updateData = UpdateMessageData.buildGroupUpdate(type, name, members)?.toJSON() ?: ""
        val infoMessage = OutgoingGroupMediaMessage(recipient, updateData, groupID, null, sentTimestamp, 0, true, null, listOf(), listOf())
        val mmsDB = DatabaseComponent.get(context).mmsDatabase()
        val mmsSmsDB = DatabaseComponent.get(context).mmsSmsDatabase()
        if (mmsSmsDB.getMessageFor(sentTimestamp, userPublicKey) != null) return
        val infoMessageID = mmsDB.insertMessageOutbox(infoMessage, threadID, false, null, runThreadUpdate = true)
        mmsDB.markAsSent(infoMessageID, true)
    }

    override fun isClosedGroup(publicKey: String): Boolean {
        val isClosedGroup = DatabaseComponent.get(context).lokiAPIDatabase().isClosedGroup(publicKey)
        val address = fromSerialized(publicKey)
        return address.isClosedGroup || isClosedGroup
    }

    override fun getClosedGroupEncryptionKeyPairs(groupPublicKey: String): MutableList<ECKeyPair> {
        return DatabaseComponent.get(context).lokiAPIDatabase().getClosedGroupEncryptionKeyPairs(groupPublicKey).toMutableList()
    }

    override fun getLatestClosedGroupEncryptionKeyPair(groupPublicKey: String): ECKeyPair? {
        return DatabaseComponent.get(context).lokiAPIDatabase().getLatestClosedGroupEncryptionKeyPair(groupPublicKey)
    }

    override fun getAllClosedGroupPublicKeys(): Set<String> {
        return DatabaseComponent.get(context).lokiAPIDatabase().getAllClosedGroupPublicKeys()
    }

    override fun getAllActiveClosedGroupPublicKeys(): Set<String> {
        return DatabaseComponent.get(context).lokiAPIDatabase().getAllClosedGroupPublicKeys().filter {
            getGroup(GroupUtil.doubleEncodeGroupID(it))?.isActive == true
        }.toSet()
    }

    override fun addClosedGroupPublicKey(groupPublicKey: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().addClosedGroupPublicKey(groupPublicKey)
    }

    override fun removeClosedGroupPublicKey(groupPublicKey: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().removeClosedGroupPublicKey(groupPublicKey)
    }

    override fun addClosedGroupEncryptionKeyPair(encryptionKeyPair: ECKeyPair, groupPublicKey: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().addClosedGroupEncryptionKeyPair(encryptionKeyPair, groupPublicKey)
    }

    override fun removeAllClosedGroupEncryptionKeyPairs(groupPublicKey: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().removeAllClosedGroupEncryptionKeyPairs(groupPublicKey)
    }

    override fun updateFormationTimestamp(groupID: String, formationTimestamp: Long) {
        DatabaseComponent.get(context).groupDatabase()
            .updateFormationTimestamp(groupID, formationTimestamp)
    }

    override fun updateTimestampUpdated(groupID: String, updatedTimestamp: Long) {
        DatabaseComponent.get(context).groupDatabase()
            .updateTimestampUpdated(groupID, updatedTimestamp)
    }

    override fun setExpirationTimer(groupID: String, duration: Int) {
        val recipient = Recipient.from(context, fromSerialized(groupID), false)
        DatabaseComponent.get(context).recipientDatabase().setExpireMessages(recipient, duration);
    }

    override fun setServerCapabilities(server: String, capabilities: List<String>) {
        return DatabaseComponent.get(context).lokiAPIDatabase().setServerCapabilities(server, capabilities)
    }

    override fun getServerCapabilities(server: String): List<String> {
        return DatabaseComponent.get(context).lokiAPIDatabase().getServerCapabilities(server)
    }

    override fun getAllOpenGroups(): Map<Long, OpenGroup> {
        return DatabaseComponent.get(context).lokiThreadDatabase().getAllOpenGroups()
    }

    override fun updateOpenGroup(openGroup: OpenGroup) {
        OpenGroupManager.updateOpenGroup(openGroup, context)
    }

    override fun getAllGroups(): List<GroupRecord> {
        return DatabaseComponent.get(context).groupDatabase().allGroups
    }

    override fun addOpenGroup(urlAsString: String): OpenGroupApi.RoomInfo? {
        return OpenGroupManager.addOpenGroup(urlAsString, context)
    }

    override fun onOpenGroupAdded(server: String) {
        OpenGroupManager.restartPollerForServer(server.removeSuffix("/"))
    }

    override fun hasBackgroundGroupAddJob(groupJoinUrl: String): Boolean {
        val jobDb = DatabaseComponent.get(context).sessionJobDatabase()
        return jobDb.hasBackgroundGroupAddJob(groupJoinUrl)
    }

    override fun setProfileSharing(address: Address, value: Boolean) {
        val recipient = Recipient.from(context, address, false)
        DatabaseComponent.get(context).recipientDatabase().setProfileSharing(recipient, value)
    }

    override fun getOrCreateThreadIdFor(address: Address): Long {
        val recipient = Recipient.from(context, address, false)
        return DatabaseComponent.get(context).threadDatabase().getOrCreateThreadIdFor(recipient)
    }

    override fun getOrCreateThreadIdFor(publicKey: String, groupPublicKey: String?, openGroupID: String?): Long {
        val database = DatabaseComponent.get(context).threadDatabase()
        return if (!openGroupID.isNullOrEmpty()) {
            val recipient = Recipient.from(context, fromSerialized(GroupUtil.getEncodedOpenGroupID(openGroupID.toByteArray())), false)
            database.getThreadIdIfExistsFor(recipient)
        } else if (!groupPublicKey.isNullOrEmpty()) {
            val recipient = Recipient.from(context, fromSerialized(GroupUtil.doubleEncodeGroupID(groupPublicKey)), false)
            database.getOrCreateThreadIdFor(recipient)
        } else {
            val recipient = Recipient.from(context, fromSerialized(publicKey), false)
            database.getOrCreateThreadIdFor(recipient)
        }
    }

    override fun getThreadId(publicKeyOrOpenGroupID: String): Long? {
        val address = fromSerialized(publicKeyOrOpenGroupID)
        return getThreadId(address)
    }

    override fun getThreadId(openGroup: OpenGroup): Long? {
        val address = fromSerialized("${openGroup.server}.${openGroup.room}")
        return getThreadId(address)
    }

    override fun getThreadId(address: Address): Long? {
        val recipient = Recipient.from(context, address, false)
        return getThreadId(recipient)
    }

    override fun getThreadId(recipient: Recipient): Long? {
        val threadID = DatabaseComponent.get(context).threadDatabase().getThreadIdIfExistsFor(recipient)
        return if (threadID < 0) null else threadID
    }

    override fun getThreadIdForMms(mmsId: Long): Long {
        val mmsDb = DatabaseComponent.get(context).mmsDatabase()
        val cursor = mmsDb.getMessage(mmsId)
        val reader = mmsDb.readerFor(cursor)
        val threadId = reader.next?.threadId
        cursor.close()
        return threadId ?: -1
    }

    override fun getContactWithSessionID(sessionID: String): Contact? {
        return DatabaseComponent.get(context).sessionContactDatabase().getContactWithSessionID(sessionID)
    }

    override fun getAllContacts(): Set<Contact> {
        return DatabaseComponent.get(context).sessionContactDatabase().getAllContacts()
    }

    override fun setContact(contact: Contact) {
        DatabaseComponent.get(context).sessionContactDatabase().setContact(contact)
        SSKEnvironment.shared.profileManager.contactUpdatedInternal(contact)
    }

    override fun getRecipientForThread(threadId: Long): Recipient? {
        return DatabaseComponent.get(context).threadDatabase().getRecipientForThreadId(threadId)
    }

    override fun getRecipientSettings(address: Address): Recipient.RecipientSettings? {
        val recipientSettings = DatabaseComponent.get(context).recipientDatabase().getRecipientSettings(address)
        return if (recipientSettings.isPresent) { recipientSettings.get() } else null
    }

    override fun addLibSessionContacts(contacts: List<LibSessionContact>) {
        val recipientDatabase = DatabaseComponent.get(context).recipientDatabase()
        val threadDatabase = DatabaseComponent.get(context).threadDatabase()
        val mappingDb = DatabaseComponent.get(context).blindedIdMappingDatabase()
        val moreContacts = contacts.filter { contact ->
            val id = SessionId(contact.id)
            id.prefix != IdPrefix.BLINDED || mappingDb.getBlindedIdMapping(contact.id).none { it.sessionId != null }
        }
        for (contact in moreContacts) {
            val address = fromSerialized(contact.id)
            val recipient = Recipient.from(context, address, true)
            val (url, key) = contact.profilePicture.let { it.url to it.key }
            // set or clear the avatar
            recipientDatabase.setProfileAvatar(recipient, url)
            recipientDatabase.setProfileKey(recipient, key)

        }
    }

    override fun addContacts(contacts: List<ConfigurationMessage.Contact>) {
        val recipientDatabase = DatabaseComponent.get(context).recipientDatabase()
        val threadDatabase = DatabaseComponent.get(context).threadDatabase()
        val mappingDb = DatabaseComponent.get(context).blindedIdMappingDatabase()
        val moreContacts = contacts.filter { contact ->
            val id = SessionId(contact.publicKey)
            id.prefix != IdPrefix.BLINDED || mappingDb.getBlindedIdMapping(contact.publicKey).none { it.sessionId != null }
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
                setRecipientApproved(recipient, true)
                threadDatabase.setHasSent(threadId, true)
            }
            if (contact.isBlocked == true) {
                setBlocked(listOf(recipient), true, fromConfigUpdate = true)
                threadDatabase.deleteConversation(threadId)
            }
        }
        if (contacts.isNotEmpty()) {
            threadDatabase.notifyConversationListListeners()
        }
    }

    override fun getLastUpdated(threadID: Long): Long {
        val threadDB = DatabaseComponent.get(context).threadDatabase()
        return threadDB.getLastUpdated(threadID)
    }

    override fun trimThread(threadID: Long, threadLimit: Int) {
        val threadDB = DatabaseComponent.get(context).threadDatabase()
        threadDB.trimThread(threadID, threadLimit)
    }

    override fun trimThreadBefore(threadID: Long, timestamp: Long) {
        val threadDB = DatabaseComponent.get(context).threadDatabase()
        threadDB.trimThreadBefore(threadID, timestamp)
    }

    override fun getMessageCount(threadID: Long): Long {
        val mmsSmsDb = DatabaseComponent.get(context).mmsSmsDatabase()
        return mmsSmsDb.getConversationCount(threadID)
    }

    override fun setPinned(threadID: Long, isPinned: Boolean) {
        val threadDB = DatabaseComponent.get(context).threadDatabase()
        threadDB.setPinned(threadID, isPinned)
    }

    override fun isPinned(threadID: Long): Boolean {
        val threadDB = DatabaseComponent.get(context).threadDatabase()
        return threadDB.isPinned(threadID)
    }

    override fun getAttachmentDataUri(attachmentId: AttachmentId): Uri {
        return PartAuthority.getAttachmentDataUri(attachmentId)
    }

    override fun getAttachmentThumbnailUri(attachmentId: AttachmentId): Uri {
        return PartAuthority.getAttachmentThumbnailUri(attachmentId)
    }

    override fun insertDataExtractionNotificationMessage(senderPublicKey: String, message: DataExtractionNotificationInfoMessage, sentTimestamp: Long) {
        val database = DatabaseComponent.get(context).mmsDatabase()
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

        database.insertSecureDecryptedMessageInbox(mediaMessage, -1, runThreadUpdate = true)
    }

    override fun insertMessageRequestResponse(response: MessageRequestResponse) {
        val userPublicKey = getUserPublicKey()
        val senderPublicKey = response.sender!!
        val recipientPublicKey = response.recipient!!
        if (userPublicKey == null || (userPublicKey != recipientPublicKey && userPublicKey != senderPublicKey)) return
        val recipientDb = DatabaseComponent.get(context).recipientDatabase()
        val threadDB = DatabaseComponent.get(context).threadDatabase()
        if (userPublicKey == senderPublicKey) {
            val requestRecipient = Recipient.from(context, fromSerialized(recipientPublicKey), false)
            recipientDb.setApproved(requestRecipient, true)
            val threadId = threadDB.getOrCreateThreadIdFor(requestRecipient)
            threadDB.setHasSent(threadId, true)
        } else {
            val mmsDb = DatabaseComponent.get(context).mmsDatabase()
            val smsDb = DatabaseComponent.get(context).smsDatabase()
            val sender = Recipient.from(context, fromSerialized(senderPublicKey), false)
            val threadId = threadDB.getOrCreateThreadIdFor(sender)
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
            threadDB.setHasSent(threadId, true)
            val mappingDb = DatabaseComponent.get(context).blindedIdMappingDatabase()
            val mappings = mutableMapOf<String, BlindedIdMapping>()
            threadDB.readerFor(threadDB.conversationList).use { reader ->
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
                    mappingDb.getBlindedIdMapping(blindedId).firstOrNull()?.let {
                        mappings[address] = it
                    }
                }
            }
            for (mapping in mappings) {
                if (!SodiumUtilities.sessionId(senderPublicKey, mapping.value.blindedId, mapping.value.serverId)) {
                    continue
                }
                mappingDb.addBlindedIdMapping(mapping.value.copy(sessionId = senderPublicKey))

                val blindedThreadId = threadDB.getOrCreateThreadIdFor(Recipient.from(context, fromSerialized(mapping.key), false))
                mmsDb.updateThreadId(blindedThreadId, threadId)
                smsDb.updateThreadId(blindedThreadId, threadId)
                threadDB.deleteConversation(blindedThreadId)
            }
            recipientDb.setApproved(sender, true)
            recipientDb.setApprovedMe(sender, true)

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
            mmsDb.insertSecureDecryptedMessageInbox(message, threadId, runThreadUpdate = true)
        }
    }

    override fun setRecipientApproved(recipient: Recipient, approved: Boolean) {
        DatabaseComponent.get(context).recipientDatabase().setApproved(recipient, approved)
        configFactory.contacts?.upsertContact(recipient.address.serialize()) {
            this.approved = approved
        }
    }

    override fun setRecipientApprovedMe(recipient: Recipient, approvedMe: Boolean) {
        DatabaseComponent.get(context).recipientDatabase().setApprovedMe(recipient, approvedMe)
        configFactory.contacts?.upsertContact(recipient.address.serialize()) {
            this.approvedMe = approvedMe
        }
    }

    override fun insertCallMessage(senderPublicKey: String, callMessageType: CallMessageType, sentTimestamp: Long) {
        val database = DatabaseComponent.get(context).smsDatabase()
        val address = fromSerialized(senderPublicKey)
        val callMessage = IncomingTextMessage.fromCallInfo(callMessageType, address, Optional.absent(), sentTimestamp)
        database.insertCallMessage(callMessage)
    }

    override fun conversationHasOutgoing(userPublicKey: String): Boolean {
        val database = DatabaseComponent.get(context).threadDatabase()
        val threadId = database.getThreadIdIfExistsFor(userPublicKey)

        if (threadId == -1L) return false

        return database.getLastSeenAndHasSent(threadId).second() ?: false
    }

    override fun getLastInboxMessageId(server: String): Long? {
        return DatabaseComponent.get(context).lokiAPIDatabase().getLastInboxMessageId(server)
    }

    override fun setLastInboxMessageId(server: String, messageId: Long) {
        DatabaseComponent.get(context).lokiAPIDatabase().setLastInboxMessageId(server, messageId)
    }

    override fun removeLastInboxMessageId(server: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().removeLastInboxMessageId(server)
    }

    override fun getLastOutboxMessageId(server: String): Long? {
        return DatabaseComponent.get(context).lokiAPIDatabase().getLastOutboxMessageId(server)
    }

    override fun setLastOutboxMessageId(server: String, messageId: Long) {
        DatabaseComponent.get(context).lokiAPIDatabase().setLastOutboxMessageId(server, messageId)
    }

    override fun removeLastOutboxMessageId(server: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().removeLastOutboxMessageId(server)
    }

    override fun getOrCreateBlindedIdMapping(
        blindedId: String,
        server: String,
        serverPublicKey: String,
        fromOutbox: Boolean
    ): BlindedIdMapping {
        val db = DatabaseComponent.get(context).blindedIdMappingDatabase()
        val mapping = db.getBlindedIdMapping(blindedId).firstOrNull() ?: BlindedIdMapping(blindedId, null, server, serverPublicKey)
        if (mapping.sessionId != null) {
            return mapping
        }
        val threadDb = DatabaseComponent.get(context).threadDatabase()
        threadDb.readerFor(threadDb.conversationList).use { reader ->
            while (reader.next != null) {
                val recipient = reader.current.recipient
                val sessionId = recipient.address.serialize()
                if (!recipient.isGroupRecipient && SodiumUtilities.sessionId(sessionId, blindedId, serverPublicKey)) {
                    val contactMapping = mapping.copy(sessionId = sessionId)
                    db.addBlindedIdMapping(contactMapping)
                    return contactMapping
                }
            }
        }
        db.getBlindedIdMappingsExceptFor(server).forEach {
            if (SodiumUtilities.sessionId(it.sessionId!!, blindedId, serverPublicKey)) {
                val otherMapping = mapping.copy(sessionId = it.sessionId)
                db.addBlindedIdMapping(otherMapping)
                return otherMapping
            }
        }
        db.addBlindedIdMapping(mapping)
        return mapping
    }

    override fun addReaction(reaction: Reaction, messageSender: String, notifyUnread: Boolean) {
        val timestamp = reaction.timestamp
        val localId = reaction.localId
        val isMms = reaction.isMms
        val messageId = if (localId != null && localId > 0 && isMms != null) {
            MessageId(localId, isMms)
        } else if (timestamp != null && timestamp > 0) {
            val messageRecord = DatabaseComponent.get(context).mmsSmsDatabase().getMessageForTimestamp(timestamp) ?: return
            MessageId(messageRecord.id, messageRecord.isMms)
        } else return
        DatabaseComponent.get(context).reactionDatabase().addReaction(
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
        val messageRecord = DatabaseComponent.get(context).mmsSmsDatabase().getMessageForTimestamp(messageTimestamp) ?: return
        val messageId = MessageId(messageRecord.id, messageRecord.isMms)
        DatabaseComponent.get(context).reactionDatabase().deleteReaction(emoji, messageId, author, notifyUnread)
    }

    override fun updateReactionIfNeeded(message: Message, sender: String, openGroupSentTimestamp: Long) {
        val database = DatabaseComponent.get(context).reactionDatabase()
        var reaction = database.getReactionFor(message.sentTimestamp!!, sender) ?: return
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
        database.updateReaction(reaction)
    }

    override fun deleteReactions(messageId: Long, mms: Boolean) {
        DatabaseComponent.get(context).reactionDatabase().deleteMessageReactions(MessageId(messageId, mms))
    }

    override fun setBlocked(recipients: List<Recipient>, isBlocked: Boolean, fromConfigUpdate: Boolean) {
        val recipientDb = DatabaseComponent.get(context).recipientDatabase()
        recipientDb.setBlocked(recipients, isBlocked)
        recipients.filter { it.isContactRecipient }.forEach { recipient ->
            configFactory.contacts?.upsertContact(recipient.address.serialize()) {
                this.blocked = isBlocked
            }
        }
        val contactsConfig = configFactory.contacts ?: return
        if (contactsConfig.needsPush() && !fromConfigUpdate) {
            Log.d("Loki-DBG", "Needs to push contacts after blocking ${recipients.map { it.toShortString() }}")
            ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(context)
        }
    }

    override fun blockedContacts(): List<Recipient> {
        val recipientDb = DatabaseComponent.get(context).recipientDatabase()
        return recipientDb.blockedContacts
    }

}