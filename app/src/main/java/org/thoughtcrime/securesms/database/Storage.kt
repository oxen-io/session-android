package org.thoughtcrime.securesms.database

import android.content.Context
import android.net.Uri
import network.loki.messenger.libsession_util.ConfigBase
import network.loki.messenger.libsession_util.ConfigBase.Companion.PRIORITY_HIDDEN
import network.loki.messenger.libsession_util.ConfigBase.Companion.PRIORITY_PINNED
import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.ConversationVolatileConfig
import network.loki.messenger.libsession_util.UserGroupsConfig
import network.loki.messenger.libsession_util.UserProfile
import network.loki.messenger.libsession_util.util.BaseCommunityInfo
import network.loki.messenger.libsession_util.util.Conversation
import network.loki.messenger.libsession_util.util.ExpiryMode
import network.loki.messenger.libsession_util.util.GroupInfo
import network.loki.messenger.libsession_util.util.UserPic
import org.session.libsession.avatars.AvatarHelper
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.BlindedIdMapping
import org.session.libsession.messaging.calls.CallMessageType
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.jobs.AttachmentUploadJob
import org.session.libsession.messaging.jobs.BackgroundGroupAddJob
import org.session.libsession.messaging.jobs.ConfigurationSyncJob
import org.session.libsession.messaging.jobs.GroupAvatarDownloadJob
import org.session.libsession.messaging.jobs.Job
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveJob
import org.session.libsession.messaging.jobs.MessageSendJob
import org.session.libsession.messaging.jobs.RetrieveProfileAvatarJob
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.control.ConfigurationMessage
import org.session.libsession.messaging.messages.control.MessageRequestResponse
import org.session.libsession.messaging.messages.signal.IncomingEncryptedMessage
import org.session.libsession.messaging.messages.signal.IncomingGroupMessage
import org.session.libsession.messaging.messages.signal.IncomingMediaMessage
import org.session.libsession.messaging.messages.signal.IncomingTextMessage
import org.session.libsession.messaging.messages.signal.OutgoingGroupMediaMessage
import org.session.libsession.messaging.messages.signal.OutgoingMediaMessage
import org.session.libsession.messaging.messages.signal.OutgoingTextMessage
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
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationAPI
import org.session.libsession.messaging.sending_receiving.pollers.ClosedGroupPollerV2
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel
import org.session.libsession.messaging.utilities.SessionId
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.messaging.utilities.UpdateMessageData
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.Address.Companion.fromSerialized
import org.session.libsession.utilities.GroupRecord
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.ProfileKeyUtil
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.crypto.ecc.DjbECPrivateKey
import org.session.libsignal.crypto.ecc.DjbECPublicKey
import org.session.libsignal.crypto.ecc.ECKeyPair
import org.session.libsignal.messages.SignalServiceAttachmentPointer
import org.session.libsignal.messages.SignalServiceGroup
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.KeyHelper
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.guava.Optional
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.groups.ClosedGroupManager
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import org.thoughtcrime.securesms.util.SessionMetaProtocol
import java.security.MessageDigest
import network.loki.messenger.libsession_util.util.Contact as LibSessionContact

open class Storage(context: Context, helper: SQLCipherOpenHelper, private val configFactory: ConfigFactory) : Database(context, helper), StorageProtocol,
    ThreadDatabase.ConversationThreadUpdateListener {

    override fun threadCreated(address: Address, threadId: Long) {
        val localUserAddress = getUserPublicKey() ?: return
        if (!getRecipientApproved(address) && localUserAddress != address.serialize()) return // don't store unapproved / message requests

        val volatile = configFactory.convoVolatile ?: return
        if (address.isGroup) {
            val groups = configFactory.userGroups ?: return
            if (address.isClosedGroup) {
                val sessionId = GroupUtil.doubleDecodeGroupId(address.serialize())
                val closedGroup = getGroup(address.toGroupString())
                if (closedGroup != null && closedGroup.isActive) {
                    val legacyGroup = groups.getOrConstructLegacyGroupInfo(sessionId)
                    groups.set(legacyGroup)
                    val newVolatileParams = volatile.getOrConstructLegacyGroup(sessionId).copy(
                        lastRead = SnodeAPI.nowWithOffset,
                    )
                    volatile.set(newVolatileParams)
                }
            } else if (address.isOpenGroup) {
                // these should be added on the group join / group info fetch
                Log.w("Loki", "Thread created called for open group address, not adding any extra information")
            }
        } else if (address.isContact) {
            // non-standard contact prefixes: 15, 00 etc shouldn't be stored in config
            if (SessionId(address.serialize()).prefix != IdPrefix.STANDARD) return
            // don't update our own address into the contacts DB
            if (getUserPublicKey() != address.serialize()) {
                val contacts = configFactory.contacts ?: return
                contacts.upsertContact(address.serialize()) {
                    priority = ConfigBase.PRIORITY_VISIBLE
                }
            } else {
                val userProfile = configFactory.user ?: return
                userProfile.setNtsPriority(ConfigBase.PRIORITY_VISIBLE)
                DatabaseComponent.get(context).threadDatabase().setHasSent(threadId, true)
            }
            val newVolatileParams = volatile.getOrConstructOneToOne(address.serialize())
            volatile.set(newVolatileParams)
        }
    }

    override fun threadDeleted(address: Address, threadId: Long) {
        val volatile = configFactory.convoVolatile ?: return
        if (address.isGroup) {
            val groups = configFactory.userGroups ?: return
            if (address.isClosedGroup) {
                val sessionId = GroupUtil.doubleDecodeGroupId(address.serialize())
                volatile.eraseLegacyClosedGroup(sessionId)
                groups.eraseLegacyGroup(sessionId)
            } else if (address.isOpenGroup) {
                // these should be removed in the group leave / handling new configs
                Log.w("Loki", "Thread delete called for open group address, expecting to be handled elsewhere")
            }
        } else {
            // non-standard contact prefixes: 15, 00 etc shouldn't be stored in config
            if (SessionId(address.serialize()).prefix != IdPrefix.STANDARD) return
            volatile.eraseOneToOne(address.serialize())
            if (getUserPublicKey() != address.serialize()) {
                val contacts = configFactory.contacts ?: return
                contacts.upsertContact(address.serialize()) {
                    priority = PRIORITY_HIDDEN
                }
            } else {
                val userProfile = configFactory.user ?: return
                userProfile.setNtsPriority(PRIORITY_HIDDEN)
            }
        }
        ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(context)
    }

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

    override fun setProfileAvatar(recipient: Recipient, profileAvatar: String?) {
        val database = DatabaseComponent.get(context).recipientDatabase()
        database.setProfileAvatar(recipient, profileAvatar)
    }

    override fun setProfilePicture(recipient: Recipient, newProfilePicture: String?, newProfileKey: ByteArray?) {
        val db = DatabaseComponent.get(context).recipientDatabase()
        db.setProfileAvatar(recipient, newProfilePicture)
        db.setProfileKey(recipient, newProfileKey)
    }

    override fun setUserProfilePicture(newProfilePicture: String?, newProfileKey: ByteArray?) {
        val ourRecipient = fromSerialized(getUserPublicKey()!!).let {
            Recipient.from(context, it, false)
        }
        ourRecipient.resolve().profileKey = newProfileKey
        TextSecurePreferences.setProfileKey(context, newProfileKey?.let { Base64.encodeBytes(it) })
        TextSecurePreferences.setProfilePictureURL(context, newProfilePicture)

        if (newProfileKey != null) {
            JobQueue.shared.add(RetrieveProfileAvatarJob(newProfilePicture, ourRecipient.address))
        }
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

    override fun markConversationAsRead(threadId: Long, lastSeenTime: Long, force: Boolean) {
        val threadDb = DatabaseComponent.get(context).threadDatabase()
        getRecipientForThread(threadId)?.let { recipient ->
            val currentLastRead = threadDb.getLastSeenAndHasSent(threadId).first()
            // don't set the last read in the volatile if we didn't set it in the DB
            if (!threadDb.markAllAsRead(threadId, recipient.isGroupRecipient, lastSeenTime, force) && !force) return

            // don't process configs for inbox recipients
            if (recipient.isOpenGroupInboxRecipient) return

            configFactory.convoVolatile?.let { config ->
                val convo = when {
                    // recipient closed group
                    recipient.isClosedGroupRecipient -> config.getOrConstructLegacyGroup(GroupUtil.doubleDecodeGroupId(recipient.address.serialize()))
                    // recipient is open group
                    recipient.isOpenGroupRecipient -> {
                        val openGroupJoinUrl = getOpenGroup(threadId)?.joinURL ?: return
                        BaseCommunityInfo.parseFullUrl(openGroupJoinUrl)?.let { (base, room, pubKey) ->
                            config.getOrConstructCommunity(base, room, pubKey)
                        } ?: return
                    }
                    // otherwise recipient is one to one
                    recipient.isContactRecipient -> {
                        // don't process non-standard session IDs though
                        val sessionId = SessionId(recipient.address.serialize())
                        if (sessionId.prefix != IdPrefix.STANDARD) return

                        config.getOrConstructOneToOne(recipient.address.serialize())
                    }
                    else -> throw NullPointerException("Weren't expecting to have a convo with address ${recipient.address.serialize()}")
                }
                convo.lastRead = lastSeenTime
                if (convo.unread) {
                    convo.unread = lastSeenTime <= currentLastRead
                    notifyConversationListListeners()
                }
                config.set(convo)
                ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(context)
            }
        }
    }

    override fun updateThread(threadId: Long, unarchive: Boolean) {
        val threadDb = DatabaseComponent.get(context).threadDatabase()
        threadDb.update(threadId, unarchive, false)
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
            if (isUserSender || isUserBlindedSender) {
                setRecipientApproved(targetRecipient, true)
            } else {
                setRecipientApprovedMe(targetRecipient, true)
            }
        }
        if (message.threadID == null && !targetRecipient.isOpenGroupRecipient) {
            // open group recipients should explicitly create threads
            message.threadID = getOrCreateThreadIdFor(targetAddress)
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
                mmsDatabase.insertSecureDecryptedMessageInbox(mediaMessage, message.threadID!!, message.receivedTimestamp ?: 0, runThreadUpdate)
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
        return DatabaseComponent.get(context).sessionJobDatabase().getAllJobs(type)
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
        return DatabaseComponent.get(context).sessionJobDatabase().getAllJobs(ConfigurationSyncJob.KEY).values.firstOrNull {
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

    override fun cancelPendingMessageSendJobs(threadID: Long) {
        val jobDb = DatabaseComponent.get(context).sessionJobDatabase()
        jobDb.cancelPendingMessageSendJobs(threadID)
    }

    override fun getAuthToken(room: String, server: String): String? {
        val id = "$server.$room"
        return DatabaseComponent.get(context).lokiAPIDatabase().getAuthToken(id)
    }

    override fun notifyConfigUpdates(forConfigObject: ConfigBase) {
        notifyUpdates(forConfigObject)
    }

    override fun conversationInConfig(publicKey: String?, groupPublicKey: String?, openGroupId: String?, visibleOnly: Boolean): Boolean {
        return configFactory.conversationInConfig(publicKey, groupPublicKey, openGroupId, visibleOnly)
    }

    override fun canPerformConfigChange(variant: String, publicKey: String, changeTimestampMs: Long): Boolean {
        return configFactory.canPerformChange(variant, publicKey, changeTimestampMs)
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
            clearUserPic()
        } else if (userPic.key.isNotEmpty() && userPic.url.isNotEmpty()
            && TextSecurePreferences.getProfilePictureURL(context) != userPic.url) {
            setUserProfilePicture(userPic.url, userPic.key)
        }
        if (userProfile.getNtsPriority() == PRIORITY_HIDDEN) {
            // delete nts thread if needed
            val ourThread = getThreadId(recipient) ?: return
            deleteConversation(ourThread)
        } else {
            // create note to self thread if needed (?)
            val ourThread = getOrCreateThreadIdFor(recipient.address)
            DatabaseComponent.get(context).threadDatabase().setHasSent(ourThread, true)
            setPinned(ourThread, userProfile.getNtsPriority() > 0)
        }

    }

    private fun updateContacts(contacts: Contacts) {
        val extracted = contacts.all().toList()
        addLibSessionContacts(extracted)
    }

    override fun clearUserPic() {
        val userPublicKey = getUserPublicKey() ?: return
        val recipientDatabase = DatabaseComponent.get(context).recipientDatabase()
        // would love to get rid of recipient and context from this
        val recipient = Recipient.from(context, fromSerialized(userPublicKey), false)
        // clear picture if userPic is null
        TextSecurePreferences.setProfileKey(context, null)
        ProfileKeyUtil.setEncodedProfileKey(context, null)
        recipientDatabase.setProfileAvatar(recipient, null)
        TextSecurePreferences.setProfileAvatarId(context, 0)
        TextSecurePreferences.setProfilePictureURL(context, null)

        Recipient.removeCached(fromSerialized(userPublicKey))
        configFactory.user?.setPic(UserPic.DEFAULT)
        ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(context)
    }

    private fun updateConvoVolatile(convos: ConversationVolatileConfig) {
        val extracted = convos.all()
        for (conversation in extracted) {
            val threadId = when (conversation) {
                is Conversation.OneToOne -> getThreadIdFor(conversation.sessionId, null, null, createThread = false)
                is Conversation.LegacyGroup -> getThreadIdFor("", conversation.groupId,null, createThread = false)
                is Conversation.Community -> getThreadIdFor("",null, "${conversation.baseCommunityInfo.baseUrl.removeSuffix("/")}.${conversation.baseCommunityInfo.room}", createThread = false)
            }
            if (threadId != null) {
                if (conversation.lastRead > getLastSeen(threadId)) {
                    markConversationAsRead(threadId, conversation.lastRead, force = true)
                }
                updateThread(threadId, false)
            }
        }
    }

    private fun updateUserGroups(userGroups: UserGroupsConfig) {
        val threadDb = DatabaseComponent.get(context).threadDatabase()
        val localUserPublicKey = getUserPublicKey() ?: return Log.w(
            "Loki",
            "No user public key when trying to update user groups from config"
        )
        val communities = userGroups.allCommunityInfo()
        val lgc = userGroups.allLegacyGroupInfo()
        val allOpenGroups = getAllOpenGroups()
        val toDeleteCommunities = allOpenGroups.filter {
            Conversation.Community(BaseCommunityInfo(it.value.server, it.value.room, it.value.publicKey), 0, false).baseCommunityInfo.fullUrl() !in communities.map { it.community.fullUrl() }
        }

        val existingCommunities: Map<Long, OpenGroup> = allOpenGroups.filterKeys { it !in toDeleteCommunities.keys }
        val toAddCommunities = communities.filter { it.community.fullUrl() !in existingCommunities.map { it.value.joinURL } }
        val existingJoinUrls = existingCommunities.values.map { it.joinURL }

        val existingClosedGroups = getAllGroups(includeInactive = true).filter { it.isClosedGroup }
        val lgcIds = lgc.map { it.sessionId }
        val toDeleteClosedGroups = existingClosedGroups.filter { group ->
            GroupUtil.doubleDecodeGroupId(group.encodedId) !in lgcIds
        }

        // delete the ones which are not listed in the config
        toDeleteCommunities.values.forEach { openGroup ->
            OpenGroupManager.delete(openGroup.server, openGroup.room, context)
        }

        toDeleteClosedGroups.forEach { deleteGroup ->
            val threadId = getThreadId(deleteGroup.encodedId)
            if (threadId != null) {
                ClosedGroupManager.silentlyRemoveGroup(context,threadId,GroupUtil.doubleDecodeGroupId(deleteGroup.encodedId), deleteGroup.encodedId, localUserPublicKey, delete = true)
            }
        }

        toAddCommunities.forEach { toAddCommunity ->
            val joinUrl = toAddCommunity.community.fullUrl()
            if (!hasBackgroundGroupAddJob(joinUrl)) {
                JobQueue.shared.add(BackgroundGroupAddJob(joinUrl))
            }
        }

        for (groupInfo in communities) {
            val groupBaseCommunity = groupInfo.community
            if (groupBaseCommunity.fullUrl() in existingJoinUrls) {
                // add it
                val (threadId, _) = existingCommunities.entries.first { (_, v) -> v.joinURL == groupInfo.community.fullUrl() }
                threadDb.setPinned(threadId, groupInfo.priority == PRIORITY_PINNED)
            }
        }

        for (group in lgc) {
            val existingGroup = existingClosedGroups.firstOrNull { GroupUtil.doubleDecodeGroupId(it.encodedId) == group.sessionId }
            val existingThread = existingGroup?.let { getThreadId(existingGroup.encodedId) }
            if (existingGroup != null) {
                if (group.priority == PRIORITY_HIDDEN && existingThread != null) {
                    ClosedGroupManager.silentlyRemoveGroup(context,existingThread,GroupUtil.doubleDecodeGroupId(existingGroup.encodedId), existingGroup.encodedId, localUserPublicKey, delete = true)
                } else if (existingThread == null) {
                    Log.w("Loki-DBG", "Existing group had no thread to hide")
                } else {
                    Log.d("Loki-DBG", "Setting existing group pinned status to ${group.priority}")
                    threadDb.setPinned(existingThread, group.priority == PRIORITY_PINNED)
                }
            } else {
                val members = group.members.keys.map { Address.fromSerialized(it) }
                val admins = group.members.filter { it.value /*admin = true*/ }.keys.map { Address.fromSerialized(it) }
                val groupId = GroupUtil.doubleEncodeGroupID(group.sessionId)
                val title = group.name
                val formationTimestamp = (group.joinedAt * 1000L)
                createGroup(groupId, title, admins + members, null, null, admins, formationTimestamp)
                setProfileSharing(Address.fromSerialized(groupId), true)
                // Add the group to the user's set of public keys to poll for
                addClosedGroupPublicKey(group.sessionId)
                // Store the encryption key pair
                val keyPair = ECKeyPair(DjbECPublicKey(group.encPubKey), DjbECPrivateKey(group.encSecKey))
                addClosedGroupEncryptionKeyPair(keyPair, group.sessionId, SnodeAPI.nowWithOffset)
                // Set expiration timer
                val expireTimer = group.disappearingTimer
                setExpirationTimer(groupId, expireTimer.toInt())
                // Notify the PN server
                PushNotificationAPI.performOperation(PushNotificationAPI.ClosedGroupOperation.Subscribe, group.sessionId, localUserPublicKey)
                // Notify the user
                val threadID = getOrCreateThreadIdFor(Address.fromSerialized(groupId))
                threadDb.setDate(threadID, formationTimestamp)
                insertOutgoingInfoMessage(context, groupId, SignalServiceGroup.Type.CREATION, title, members.map { it.serialize() }, admins.map { it.serialize() }, threadID, formationTimestamp)
                // Don't create config group here, it's from a config update
                // Start polling
                ClosedGroupPollerV2.shared.startPolling(group.sessionId)
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

    override fun markAsSyncing(timestamp: Long, author: String) {
        DatabaseComponent.get(context).mmsSmsDatabase()
            .getMessageFor(timestamp, author)
            ?.run { getMmsDatabaseElseSms(isMms).markAsSyncing(id) }
    }

    private fun getMmsDatabaseElseSms(isMms: Boolean) =
        if (isMms) DatabaseComponent.get(context).mmsDatabase()
        else DatabaseComponent.get(context).smsDatabase()

    override fun markAsResyncing(timestamp: Long, author: String) {
        DatabaseComponent.get(context).mmsSmsDatabase()
            .getMessageFor(timestamp, author)
            ?.run { getMmsDatabaseElseSms(isMms).markAsResyncing(id) }
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

    override fun markAsSentFailed(timestamp: Long, author: String, error: Exception) {
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

    override fun markAsSyncFailed(timestamp: Long, author: String, error: Exception) {
        val database = DatabaseComponent.get(context).mmsSmsDatabase()
        val messageRecord = database.getMessageFor(timestamp, author) ?: return

        database.getMessageFor(timestamp, author)
            ?.run { getMmsDatabaseElseSms(isMms).markAsSyncFailed(id) }

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
    }

    override fun createInitialConfigGroup(groupPublicKey: String, name: String, members: Map<String, Boolean>, formationTimestamp: Long, encryptionKeyPair: ECKeyPair) {
        val volatiles = configFactory.convoVolatile ?: return
        val userGroups = configFactory.userGroups ?: return
        val groupVolatileConfig = volatiles.getOrConstructLegacyGroup(groupPublicKey)
        groupVolatileConfig.lastRead = formationTimestamp
        volatiles.set(groupVolatileConfig)
        val groupInfo = GroupInfo.LegacyGroupInfo(
            sessionId = groupPublicKey,
            name = name,
            members = members,
            priority = ConfigBase.PRIORITY_VISIBLE,
            encPubKey = (encryptionKeyPair.publicKey as DjbECPublicKey).publicKey,  // 'serialize()' inserts an extra byte
            encSecKey = encryptionKeyPair.privateKey.serialize(),
            disappearingTimer = 0L,
            joinedAt = (formationTimestamp / 1000L)
        )
        // shouldn't exist, don't use getOrConstruct + copy
        userGroups.set(groupInfo)
        ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(context)
    }

    override fun updateGroupConfig(groupPublicKey: String) {
        val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
        val groupAddress = fromSerialized(groupID)
        // TODO: probably add a check in here for isActive?
        // TODO: also check if local user is a member / maybe run delete otherwise?
        val existingGroup = getGroup(groupID)
            ?: return Log.w("Loki-DBG", "No existing group for ${groupPublicKey.take(4)}} when updating group config")
        val userGroups = configFactory.userGroups ?: return
        if (!existingGroup.isActive) {
            userGroups.eraseLegacyGroup(groupPublicKey)
            return
        }
        val name = existingGroup.title
        val admins = existingGroup.admins.map { it.serialize() }
        val members = existingGroup.members.map { it.serialize() }
        val membersMap = GroupUtil.createConfigMemberMap(admins = admins, members = members)
        val latestKeyPair = getLatestClosedGroupEncryptionKeyPair(groupPublicKey)
            ?: return Log.w("Loki-DBG", "No latest closed group encryption key pair for ${groupPublicKey.take(4)}} when updating group config")
        val recipientSettings = getRecipientSettings(groupAddress) ?: return
        val threadID = getThreadId(groupAddress) ?: return
        val groupInfo = userGroups.getOrConstructLegacyGroupInfo(groupPublicKey).copy(
            name = name,
            members = membersMap,
            encPubKey = (latestKeyPair.publicKey as DjbECPublicKey).publicKey,  // 'serialize()' inserts an extra byte
            encSecKey = latestKeyPair.privateKey.serialize(),
            priority = if (isPinned(threadID)) PRIORITY_PINNED else ConfigBase.PRIORITY_VISIBLE,
            disappearingTimer = recipientSettings.expireMessages.toLong(),
            joinedAt = (existingGroup.formationTimestamp / 1000L)
        )
        userGroups.set(groupInfo)
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

    override fun addClosedGroupEncryptionKeyPair(encryptionKeyPair: ECKeyPair, groupPublicKey: String, timestamp: Long) {
        DatabaseComponent.get(context).lokiAPIDatabase().addClosedGroupEncryptionKeyPair(encryptionKeyPair, groupPublicKey, timestamp)
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

    override fun setExpirationTimer(address: String, duration: Int) {
        val recipient = Recipient.from(context, fromSerialized(address), false)
        DatabaseComponent.get(context).recipientDatabase().setExpireMessages(recipient, duration)
        if (recipient.isContactRecipient && !recipient.isLocalNumber) {
            configFactory.contacts?.upsertContact(address) {
                this.expiryMode = if (duration != 0) {
                    ExpiryMode.AfterRead(duration.toLong())
                } else { // = 0 / delete
                    ExpiryMode.NONE
                }
            }
            if (configFactory.contacts?.needsPush() == true) {
                ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(context)
            }
        }
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

    override fun getAllGroups(includeInactive: Boolean): List<GroupRecord> {
        return DatabaseComponent.get(context).groupDatabase().getAllGroups(includeInactive)
    }

    override fun addOpenGroup(urlAsString: String): OpenGroupApi.RoomInfo? {
        return OpenGroupManager.addOpenGroup(urlAsString, context)
    }

    override fun onOpenGroupAdded(server: String, room: String) {
        OpenGroupManager.restartPollerForServer(server.removeSuffix("/"))
        val groups = configFactory.userGroups ?: return
        val volatileConfig = configFactory.convoVolatile ?: return
        val openGroup = getOpenGroup(room, server) ?: return
        val (infoServer, infoRoom, pubKey) = BaseCommunityInfo.parseFullUrl(openGroup.joinURL) ?: return
        val pubKeyHex = Hex.toStringCondensed(pubKey)
        val communityInfo = groups.getOrConstructCommunityInfo(infoServer, infoRoom, pubKeyHex)
        groups.set(communityInfo)
        val volatile = volatileConfig.getOrConstructCommunity(infoServer, infoRoom, pubKey)
        if (volatile.lastRead != 0L) {
            val threadId = getThreadId(openGroup) ?: return
            markConversationAsRead(threadId, volatile.lastRead, force = true)
        }
        volatileConfig.set(volatile)
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

    override fun getThreadIdFor(publicKey: String, groupPublicKey: String?, openGroupID: String?, createThread: Boolean): Long? {
        val database = DatabaseComponent.get(context).threadDatabase()
        return if (!openGroupID.isNullOrEmpty()) {
            val recipient = Recipient.from(context, fromSerialized(GroupUtil.getEncodedOpenGroupID(openGroupID.toByteArray())), false)
            database.getThreadIdIfExistsFor(recipient).let { if (it == -1L) null else it }
        } else if (!groupPublicKey.isNullOrEmpty()) {
            val recipient = Recipient.from(context, fromSerialized(GroupUtil.doubleEncodeGroupID(groupPublicKey)), false)
            if (createThread) database.getOrCreateThreadIdFor(recipient)
            else database.getThreadIdIfExistsFor(recipient).let { if (it == -1L) null else it }
        } else {
            val recipient = Recipient.from(context, fromSerialized(publicKey), false)
            if (createThread) database.getOrCreateThreadIdFor(recipient)
            else database.getThreadIdIfExistsFor(recipient).let { if (it == -1L) null else it }
        }
    }

    override fun getThreadId(publicKeyOrOpenGroupID: String): Long? {
        val address = fromSerialized(publicKeyOrOpenGroupID)
        return getThreadId(address)
    }

    override fun getThreadId(openGroup: OpenGroup): Long? {
        return GroupManager.getOpenGroupThreadID("${openGroup.server.removeSuffix("/")}.${openGroup.room}", context)
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
        val address = fromSerialized(contact.sessionID)
        if (!getRecipientApproved(address)) return
        val recipientHash = SSKEnvironment.shared.profileManager.contactUpdatedInternal(contact)
        val recipient = Recipient.from(context, address, false)
        setRecipientHash(recipient, recipientHash)
    }

    override fun getRecipientForThread(threadId: Long): Recipient? {
        return DatabaseComponent.get(context).threadDatabase().getRecipientForThreadId(threadId)
    }

    override fun getRecipientSettings(address: Address): Recipient.RecipientSettings? {
        val recipientSettings = DatabaseComponent.get(context).recipientDatabase().getRecipientSettings(address)
        return if (recipientSettings.isPresent) { recipientSettings.get() } else null
    }

    override fun addLibSessionContacts(contacts: List<LibSessionContact>) {
        val mappingDb = DatabaseComponent.get(context).blindedIdMappingDatabase()
        val moreContacts = contacts.filter { contact ->
            val id = SessionId(contact.id)
            id.prefix?.isBlinded() == false || mappingDb.getBlindedIdMapping(contact.id).none { it.sessionId != null }
        }
        val profileManager = SSKEnvironment.shared.profileManager
        moreContacts.forEach { contact ->
            val address = fromSerialized(contact.id)
            val recipient = Recipient.from(context, address, false)
            setBlocked(listOf(recipient), contact.blocked, fromConfigUpdate = true)
            setRecipientApproved(recipient, contact.approved)
            setRecipientApprovedMe(recipient, contact.approvedMe)
            if (contact.name.isNotEmpty()) {
                profileManager.setName(context, recipient, contact.name)
            } else {
                profileManager.setName(context, recipient, null)
            }
            if (contact.nickname.isNotEmpty()) {
                profileManager.setNickname(context, recipient, contact.nickname)
            } else {
                profileManager.setNickname(context, recipient, null)
            }

            if (contact.profilePicture != UserPic.DEFAULT) {
                val (url, key) = contact.profilePicture
                if (key.size != ProfileKeyUtil.PROFILE_KEY_BYTES) return@forEach
                profileManager.setProfilePicture(context, recipient, url, key)
                profileManager.setUnidentifiedAccessMode(context, recipient, Recipient.UnidentifiedAccessMode.UNKNOWN)
            } else {
                profileManager.setProfilePicture(context, recipient, null, null)
            }
            if (contact.priority == PRIORITY_HIDDEN) {
                getThreadId(fromSerialized(contact.id))?.let { conversationThreadId ->
                    deleteConversation(conversationThreadId)
                }
            } else {
                getThreadId(fromSerialized(contact.id))?.let { conversationThreadId ->
                    setPinned(conversationThreadId, contact.priority == PRIORITY_PINNED)
                }
            }
            setRecipientHash(recipient, contact.hashCode().toString())
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
            val threadId = threadDatabase.getThreadIdIfExistsFor(recipient)
            if (contact.didApproveMe == true) {
                recipientDatabase.setApprovedMe(recipient, true)
            }
            if (contact.isApproved == true && threadId != -1L) {
                setRecipientApproved(recipient, true)
                threadDatabase.setHasSent(threadId, true)
            }

            val contactIsBlocked: Boolean? = contact.isBlocked
            if (contactIsBlocked != null && recipient.isBlocked != contactIsBlocked) {
                setBlocked(listOf(recipient), contactIsBlocked, fromConfigUpdate = true)
            }
        }
        if (contacts.isNotEmpty()) {
            threadDatabase.notifyConversationListListeners()
        }
    }

    override fun setRecipientHash(recipient: Recipient, recipientHash: String?) {
        val recipientDb = DatabaseComponent.get(context).recipientDatabase()
        recipientDb.setRecipientHash(recipient, recipientHash)
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
        val threadRecipient = getRecipientForThread(threadID) ?: return
        if (threadRecipient.isLocalNumber) {
            val user = configFactory.user ?: return
            user.setNtsPriority(if (isPinned) PRIORITY_PINNED else ConfigBase.PRIORITY_VISIBLE)
        } else if (threadRecipient.isContactRecipient) {
            val contacts = configFactory.contacts ?: return
            contacts.upsertContact(threadRecipient.address.serialize()) {
                priority = if (isPinned) PRIORITY_PINNED else ConfigBase.PRIORITY_VISIBLE
            }
        } else if (threadRecipient.isGroupRecipient) {
            val groups = configFactory.userGroups ?: return
            if (threadRecipient.isClosedGroupRecipient) {
                val sessionId = GroupUtil.doubleDecodeGroupId(threadRecipient.address.serialize())
                val newGroupInfo = groups.getOrConstructLegacyGroupInfo(sessionId).copy (
                    priority = if (isPinned) PRIORITY_PINNED else ConfigBase.PRIORITY_VISIBLE
                )
                groups.set(newGroupInfo)
            } else if (threadRecipient.isOpenGroupRecipient) {
                val openGroup = getOpenGroup(threadID) ?: return
                val (baseUrl, room, pubKeyHex) = BaseCommunityInfo.parseFullUrl(openGroup.joinURL) ?: return
                val newGroupInfo = groups.getOrConstructCommunityInfo(baseUrl, room, Hex.toStringCondensed(pubKeyHex)).copy (
                    priority = if (isPinned) PRIORITY_PINNED else ConfigBase.PRIORITY_VISIBLE
                )
                groups.set(newGroupInfo)
            }
        }
        ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(context)
    }

    override fun isPinned(threadID: Long): Boolean {
        val threadDB = DatabaseComponent.get(context).threadDatabase()
        return threadDB.isPinned(threadID)
    }

    override fun setThreadDate(threadId: Long, newDate: Long) {
        val threadDb = DatabaseComponent.get(context).threadDatabase()
        threadDb.setDate(threadId, newDate)
    }

    override fun deleteConversation(threadID: Long) {
        val recipient = getRecipientForThread(threadID)
        val threadDB = DatabaseComponent.get(context).threadDatabase()
        val groupDB = DatabaseComponent.get(context).groupDatabase()
        threadDB.deleteConversation(threadID)
        if (recipient != null) {
            if (recipient.isContactRecipient) {
                if (recipient.isLocalNumber) return
                val contacts = configFactory.contacts ?: return
                contacts.upsertContact(recipient.address.serialize()) {
                    this.priority = PRIORITY_HIDDEN
                }
                ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(context)
            } else if (recipient.isClosedGroupRecipient) {
                // TODO: handle closed group
                val volatile = configFactory.convoVolatile ?: return
                val groups = configFactory.userGroups ?: return
                val groupID = recipient.address.toGroupString()
                val closedGroup = getGroup(groupID)
                val groupPublicKey = GroupUtil.doubleDecodeGroupId(recipient.address.serialize())
                if (closedGroup != null) {
                    groupDB.delete(groupID) // TODO: Should we delete the group? (seems odd to leave it)
                    volatile.eraseLegacyClosedGroup(groupPublicKey)
                    groups.eraseLegacyGroup(groupPublicKey)
                } else {
                    Log.w("Loki-DBG", "Failed to find a closed group for ${groupPublicKey.take(4)}")
                }
            }
        }
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

        val threadId = getThreadId(recipient) ?: return

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

        database.insertSecureDecryptedMessageInbox(mediaMessage, threadId, runThreadUpdate = true)
    }

    override fun insertMessageRequestResponse(response: MessageRequestResponse) {
        val userPublicKey = getUserPublicKey()
        val senderPublicKey = response.sender!!
        val recipientPublicKey = response.recipient!!

        if (
            userPublicKey == null
            || (userPublicKey != recipientPublicKey && userPublicKey != senderPublicKey)
            // this is true if it is a sync message
            || (userPublicKey == recipientPublicKey && userPublicKey == senderPublicKey)
        ) return

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
            val threadId = getOrCreateThreadIdFor(sender.address)
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
                    profileManager.setProfilePicture(context, sender, profile.profilePictureURL!!, newProfileKey!!)
                    profileManager.setUnidentifiedAccessMode(context, sender, Recipient.UnidentifiedAccessMode.UNKNOWN)
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

    override fun getRecipientApproved(address: Address): Boolean {
        return DatabaseComponent.get(context).recipientDatabase().getApproved(address)
    }

    override fun setRecipientApproved(recipient: Recipient, approved: Boolean) {
        DatabaseComponent.get(context).recipientDatabase().setApproved(recipient, approved)
        if (recipient.isLocalNumber || !recipient.isContactRecipient) return
        configFactory.contacts?.upsertContact(recipient.address.serialize()) {
            this.approved = approved
        }
    }

    override fun setRecipientApprovedMe(recipient: Recipient, approvedMe: Boolean) {
        DatabaseComponent.get(context).recipientDatabase().setApprovedMe(recipient, approvedMe)
        if (recipient.isLocalNumber || !recipient.isContactRecipient) return
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

    override fun setBlocked(recipients: Iterable<Recipient>, isBlocked: Boolean, fromConfigUpdate: Boolean) {
        val recipientDb = DatabaseComponent.get(context).recipientDatabase()
        recipientDb.setBlocked(recipients, isBlocked)
        recipients.filter { it.isContactRecipient && !it.isLocalNumber }.forEach { recipient ->
            configFactory.contacts?.upsertContact(recipient.address.serialize()) {
                this.blocked = isBlocked
            }
        }
        val contactsConfig = configFactory.contacts ?: return
        if (contactsConfig.needsPush() && !fromConfigUpdate) {
            ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(context)
        }
    }

    override fun blockedContacts(): List<Recipient> {
        val recipientDb = DatabaseComponent.get(context).recipientDatabase()
        return recipientDb.blockedContacts
    }
}