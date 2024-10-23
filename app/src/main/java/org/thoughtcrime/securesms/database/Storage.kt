package org.thoughtcrime.securesms.database

import android.content.Context
import android.net.Uri
import com.goterl.lazysodium.utils.KeyPair
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import network.loki.messenger.libsession_util.ConfigBase.Companion.PRIORITY_HIDDEN
import network.loki.messenger.libsession_util.ConfigBase.Companion.PRIORITY_PINNED
import network.loki.messenger.libsession_util.ConfigBase.Companion.PRIORITY_VISIBLE
import network.loki.messenger.libsession_util.util.BaseCommunityInfo
import network.loki.messenger.libsession_util.util.ExpiryMode
import network.loki.messenger.libsession_util.util.GroupDisplayInfo
import network.loki.messenger.libsession_util.util.GroupInfo
import network.loki.messenger.libsession_util.util.UserPic
import org.session.libsession.avatars.AvatarHelper
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.BlindedIdMapping
import org.session.libsession.messaging.calls.CallMessageType
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.jobs.AttachmentUploadJob
import org.session.libsession.messaging.jobs.GroupAvatarDownloadJob
import org.session.libsession.messaging.jobs.Job
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveJob
import org.session.libsession.messaging.jobs.MessageSendJob
import org.session.libsession.messaging.jobs.RetrieveProfileAvatarJob
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.control.ConfigurationMessage
import org.session.libsession.messaging.messages.control.GroupUpdated
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
import org.session.libsession.messaging.sending_receiving.notifications.MessageNotifier
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.messaging.utilities.UpdateMessageData
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.SnodeClock
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.Address.Companion.fromSerialized
import org.session.libsession.utilities.GroupRecord
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.ProfileKeyUtil
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.getClosedGroup
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.recipients.Recipient.DisappearingState
import org.session.libsession.utilities.recipients.MessageType
import org.session.libsession.utilities.recipients.getType
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
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.guava.Optional
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.crypto.KeyPairUtilities
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.SessionMetaProtocol
import javax.inject.Inject
import javax.inject.Singleton
import network.loki.messenger.libsession_util.util.Contact as LibSessionContact
import network.loki.messenger.libsession_util.util.GroupMember as LibSessionGroupMember

private const val TAG = "Storage"

@Singleton
open class Storage @Inject constructor(
    @ApplicationContext context: Context,
    helper: SQLCipherOpenHelper,
    private val configFactory: ConfigFactory,
    private val jobDatabase: SessionJobDatabase,
    private val threadDatabase: ThreadDatabase,
    private val recipientDatabase: RecipientDatabase,
    private val attachmentDatabase: AttachmentDatabase,
    private val lokiAPIDatabase: LokiAPIDatabase,
    private val groupDatabase: GroupDatabase,
    private val lokiMessageDatabase: LokiMessageDatabase,
    private val mmsSmsDatabase: MmsSmsDatabase,
    private val mmsDatabase: MmsDatabase,
    private val smsDatabase: SmsDatabase,
    private val blindedIdMappingDatabase: BlindedIdMappingDatabase,
    private val groupMemberDatabase: GroupMemberDatabase,
    private val reactionDatabase: ReactionDatabase,
    private val lokiThreadDatabase: LokiThreadDatabase,
    private val sessionContactDatabase: SessionContactDatabase,
    private val expirationConfigurationDatabase: ExpirationConfigurationDatabase,
    private val profileManager: SSKEnvironment.ProfileManagerProtocol,
    private val notificationManager: MessageNotifier,
    private val messageDataProvider: MessageDataProvider,
    private val messageExpirationManager: SSKEnvironment.MessageExpirationManagerProtocol,
    private val clock: SnodeClock,
    private val preferences: TextSecurePreferences,
) : Database(context, helper), StorageProtocol, ThreadDatabase.ConversationThreadUpdateListener {

    init {
        threadDatabase.setUpdateListener(this)
    }

    override fun threadCreated(address: Address, threadId: Long) {
        val localUserAddress = getUserPublicKey() ?: return
        if (!getRecipientApproved(address) && localUserAddress != address.serialize()) return // don't store unapproved / message requests

        if (address.isGroup) {
            when {
                address.isLegacyClosedGroup -> {
                    val accountId = GroupUtil.doubleDecodeGroupId(address.serialize())
                    val closedGroup = getGroup(address.toGroupString())
                    if (closedGroup != null && closedGroup.isActive) {
                        configFactory.withMutableUserConfigs { configs ->
                            val legacyGroup = configs.userGroups.getOrConstructLegacyGroupInfo(accountId)
                            configs.userGroups.set(legacyGroup)
                            val newVolatileParams = configs.convoInfoVolatile.getOrConstructLegacyGroup(accountId).copy(
                                lastRead = clock.currentTimeMills(),
                            )
                            configs.convoInfoVolatile.set(newVolatileParams)
                        }

                    }
                }
                address.isClosedGroupV2 -> {
                    configFactory.withMutableUserConfigs { configs ->
                        val accountId = address.serialize()
                        configs.userGroups.getClosedGroup(accountId)
                            ?: return@withMutableUserConfigs Log.d("Closed group doesn't exist locally", NullPointerException())

                        configs.convoInfoVolatile.getOrConstructClosedGroup(accountId)
                    }

                }
                address.isCommunity -> {
                    // these should be added on the group join / group info fetch
                    Log.w("Loki", "Thread created called for open group address, not adding any extra information")
                }
            }
        } else if (address.isContact) {
            // non-standard contact prefixes: 15, 00 etc shouldn't be stored in config
            if (AccountId(address.serialize()).prefix != IdPrefix.STANDARD) return
            // don't update our own address into the contacts DB
            if (getUserPublicKey() != address.serialize()) {
                configFactory.withMutableUserConfigs { configs ->
                    configs.contacts.upsertContact(address.serialize()) {
                        priority = PRIORITY_VISIBLE
                    }
                }
            } else {
                configFactory.withMutableUserConfigs { configs ->
                    configs.userProfile.setNtsPriority(PRIORITY_VISIBLE)
                }

                threadDatabase.setHasSent(threadId, true)
            }

            configFactory.withMutableUserConfigs { configs ->
                configs.convoInfoVolatile.getOrConstructOneToOne(address.serialize())
            }
        }
    }

    override fun threadDeleted(address: Address, threadId: Long) {
        configFactory.withMutableUserConfigs { configs ->
            if (address.isGroup) {
                if (address.isLegacyClosedGroup) {
                    val accountId = GroupUtil.doubleDecodeGroupId(address.serialize())
                    configs.convoInfoVolatile.eraseLegacyClosedGroup(accountId)
                    configs.userGroups.eraseLegacyGroup(accountId)
                } else if (address.isCommunity) {
                    // these should be removed in the group leave / handling new configs
                    Log.w("Loki", "Thread delete called for open group address, expecting to be handled elsewhere")
                } else if (address.isClosedGroupV2) {
                    Log.w("Loki", "Thread delete called for closed group address, expecting to be handled elsewhere")
                }
            } else {
                // non-standard contact prefixes: 15, 00 etc shouldn't be stored in config
                if (AccountId(address.serialize()).prefix != IdPrefix.STANDARD) return@withMutableUserConfigs
                configs.convoInfoVolatile.eraseOneToOne(address.serialize())
                if (getUserPublicKey() != address.serialize()) {
                    configs.contacts.upsertContact(address.serialize()) {
                        priority = PRIORITY_HIDDEN
                    }
                } else {
                    configs.userProfile.setNtsPriority(PRIORITY_HIDDEN)
                }
            }

            Unit
        }
    }

    override fun getUserPublicKey(): String? {
        return preferences.getLocalNumber()
    }

    override fun getUserX25519KeyPair(): ECKeyPair {
        return lokiAPIDatabase.getUserX25519KeyPair()
    }

    override fun getUserED25519KeyPair(): KeyPair? {
        return KeyPairUtilities.getUserED25519KeyPair(context)
    }

    override fun getUserProfile(): Profile {
        val displayName = TextSecurePreferences.getProfileName(context)
        val profileKey = ProfileKeyUtil.getProfileKey(context)
        val profilePictureUrl = TextSecurePreferences.getProfilePictureURL(context)
        return Profile(displayName, profileKey, profilePictureUrl)
    }

    override fun setProfileAvatar(recipient: Recipient, profileAvatar: String?) {
        val database = recipientDatabase
        database.setProfileAvatar(recipient, profileAvatar)
    }

    override fun setProfilePicture(recipient: Recipient, newProfilePicture: String?, newProfileKey: ByteArray?) {
        val db = recipientDatabase
        db.setProfileAvatar(recipient, newProfilePicture)
        db.setProfileKey(recipient, newProfileKey)
    }

    override fun setBlocksCommunityMessageRequests(recipient: Recipient, blocksMessageRequests: Boolean) {
        val db = recipientDatabase
        db.setBlocksCommunityMessageRequests(recipient, blocksMessageRequests)
    }

    override fun setUserProfilePicture(newProfilePicture: String?, newProfileKey: ByteArray?) {
        val ourRecipient = fromSerialized(getUserPublicKey()!!).let {
            Recipient.from(context, it, false)
        }
        ourRecipient.resolve().profileKey = newProfileKey
        preferences.setProfileKey(newProfileKey?.let { Base64.encodeBytes(it) })
        preferences.setProfilePictureURL(newProfilePicture)

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
        val database = attachmentDatabase
        val databaseAttachments = attachments.mapNotNull { it.toSignalAttachment() }
        return database.insertAttachments(messageID, databaseAttachments)
    }

    override fun getAttachmentsForMessage(messageID: Long): List<DatabaseAttachment> {
        return attachmentDatabase.getAttachmentsForMessage(messageID)
    }

    override fun getLastSeen(threadId: Long): Long {
        return threadDatabase.getLastSeenAndHasSent(threadId)?.first() ?: 0L
    }

    override fun ensureMessageHashesAreSender(
        hashes: Set<String>,
        sender: String,
        closedGroupId: String
    ): Boolean {
        val threadId = getThreadId(fromSerialized(closedGroupId))!!
        val info = lokiMessageDatabase.getSendersForHashes(threadId, hashes)
        return info.all { it.sender == sender }
    }

    override fun deleteMessagesByHash(threadId: Long, hashes: List<String>) {
        val info = lokiMessageDatabase.getSendersForHashes(threadId, hashes.toSet())
        for ((serverHash, sender, messageIdToDelete, isSms) in info) {
            messageDataProvider.deleteMessage(messageIdToDelete, isSms)
            if (!messageDataProvider.isOutgoingMessage(messageIdToDelete)) {
                notificationManager.updateNotification(context)
            }
        }
    }

    override fun deleteMessagesByUser(threadId: Long, userSessionId: String) {
        val userMessages = mmsSmsDatabase.getUserMessages(threadId, userSessionId)
        val (mmsMessages, smsMessages) = userMessages.partition { it.isMms }
        if (mmsMessages.isNotEmpty()) {
            messageDataProvider.deleteMessages(mmsMessages.map(MessageRecord::id), threadId, isSms = false)
        }
        if (smsMessages.isNotEmpty()) {
            messageDataProvider.deleteMessages(smsMessages.map(MessageRecord::id), threadId, isSms = true)
        }
    }

    override fun markConversationAsRead(threadId: Long, lastSeenTime: Long, force: Boolean) {
        val threadDb = threadDatabase
        getRecipientForThread(threadId)?.let { recipient ->
            val currentLastRead = threadDb.getLastSeenAndHasSent(threadId).first()
            // don't set the last read in the volatile if we didn't set it in the DB
            if (!threadDb.markAllAsRead(threadId, recipient.isGroupRecipient, lastSeenTime, force) && !force) return

            // don't process configs for inbox recipients
            if (recipient.isOpenGroupInboxRecipient) return

            configFactory.withMutableUserConfigs { configs ->
                val config = configs.convoInfoVolatile
                val convo = when {
                    // recipient closed group
                    recipient.isLegacyClosedGroupRecipient -> config.getOrConstructLegacyGroup(GroupUtil.doubleDecodeGroupId(recipient.address.serialize()))
                    recipient.isClosedGroupV2Recipient -> config.getOrConstructClosedGroup(recipient.address.serialize())
                    // recipient is open group
                    recipient.isCommunityRecipient -> {
                        val openGroupJoinUrl = getOpenGroup(threadId)?.joinURL ?: return@withMutableUserConfigs
                        BaseCommunityInfo.parseFullUrl(openGroupJoinUrl)?.let { (base, room, pubKey) ->
                            config.getOrConstructCommunity(base, room, pubKey)
                        } ?: return@withMutableUserConfigs
                    }
                    // otherwise recipient is one to one
                    recipient.isContactRecipient -> {
                        // don't process non-standard account IDs though
                        if (AccountId(recipient.address.serialize()).prefix != IdPrefix.STANDARD) return@withMutableUserConfigs
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
            }
        }
    }

    override fun updateThread(threadId: Long, unarchive: Boolean) {
        val threadDb = threadDatabase
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
        val isUserBlindedSender = message.threadID?.takeIf { it >= 0 }?.let(::getOpenGroup)?.publicKey
            ?.let { SodiumUtilities.accountId(getUserPublicKey()!!, message.sender!!, it) } ?: false
        val group: Optional<SignalServiceGroup> = when {
            openGroupID != null -> Optional.of(SignalServiceGroup(openGroupID.toByteArray(), SignalServiceGroup.GroupType.PUBLIC_CHAT))
            groupPublicKey != null && groupPublicKey.startsWith(IdPrefix.GROUP.value) -> {
                Optional.of(SignalServiceGroup(Hex.fromStringCondensed(groupPublicKey), SignalServiceGroup.GroupType.SIGNAL))
            }
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
            val idHex = group.get().groupId.toHexString()
            if (idHex.startsWith(IdPrefix.GROUP.value)) {
                fromSerialized(idHex)
            } else {
                fromSerialized(GroupUtil.getEncodedId(group.get()))
            }
        } else if (message.recipient?.startsWith(IdPrefix.GROUP.value) == true) {
            fromSerialized(message.recipient!!)
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
        if (message.threadID == null && !targetRecipient.isCommunityRecipient) {
            // open group recipients should explicitly create threads
            message.threadID = getOrCreateThreadIdFor(targetAddress)
        }
        val expiryMode = message.expiryMode
        val expiresInMillis = expiryMode.expiryMillis
        val expireStartedAt = if (expiryMode is ExpiryMode.AfterSend) message.sentTimestamp!! else 0
        if (message.isMediaMessage() || attachments.isNotEmpty()) {
            val quote: Optional<QuoteModel> = if (quotes != null) Optional.of(quotes) else Optional.absent()
            val linkPreviews: Optional<List<LinkPreview>> = if (linkPreview.isEmpty()) Optional.absent() else Optional.of(linkPreview.mapNotNull { it!! })
            val insertResult = if (isUserSender || isUserBlindedSender) {
                val mediaMessage = OutgoingMediaMessage.from(
                    message,
                    targetRecipient,
                    pointers,
                    quote.orNull(),
                    linkPreviews.orNull()?.firstOrNull(),
                    expiresInMillis,
                    expireStartedAt
                )
                mmsDatabase.insertSecureDecryptedMessageOutbox(mediaMessage, message.threadID ?: -1, message.sentTimestamp!!, runThreadUpdate)
            } else {
                // It seems like we have replaced SignalServiceAttachment with SessionServiceAttachment
                val signalServiceAttachments = attachments.mapNotNull {
                    it.toSignalPointer()
                }
                val mediaMessage = IncomingMediaMessage.from(message, senderAddress, expiresInMillis, expireStartedAt, group, signalServiceAttachments, quote, linkPreviews)
                mmsDatabase.insertSecureDecryptedMessageInbox(mediaMessage, message.threadID!!, message.receivedTimestamp ?: 0, runThreadUpdate)
            }
            if (insertResult.isPresent) {
                messageID = insertResult.get().messageId
            }
        } else {
            val isOpenGroupInvitation = (message.openGroupInvitation != null)

            val insertResult = if (isUserSender || isUserBlindedSender) {
                val textMessage = if (isOpenGroupInvitation) OutgoingTextMessage.fromOpenGroupInvitation(message.openGroupInvitation, targetRecipient, message.sentTimestamp, expiresInMillis, expireStartedAt)
                else OutgoingTextMessage.from(message, targetRecipient, expiresInMillis, expireStartedAt)
                smsDatabase.insertMessageOutbox(message.threadID ?: -1, textMessage, message.sentTimestamp!!, runThreadUpdate)
            } else {
                val textMessage = if (isOpenGroupInvitation) IncomingTextMessage.fromOpenGroupInvitation(message.openGroupInvitation, senderAddress, message.sentTimestamp, expiresInMillis, expireStartedAt)
                else IncomingTextMessage.from(message, senderAddress, group, expiresInMillis, expireStartedAt)
                val encrypted = IncomingEncryptedMessage(textMessage, textMessage.messageBody)
                smsDatabase.insertMessageInbox(encrypted, message.receivedTimestamp ?: 0, runThreadUpdate)
            }
            insertResult.orNull()?.let { result ->
                messageID = result.messageId
            }
        }
        message.serverHash?.let { serverHash ->
            messageID?.let { id ->
                lokiMessageDatabase.setMessageServerHash(id, message.isMediaMessage(), serverHash)
            }
        }
        return messageID
    }

    override fun persistJob(job: Job) {
        jobDatabase.persistJob(job)
    }

    override fun markJobAsSucceeded(jobId: String) {
        jobDatabase.markJobAsSucceeded(jobId)
    }

    override fun markJobAsFailedPermanently(jobId: String) {
        jobDatabase.markJobAsFailedPermanently(jobId)
    }

    override fun getAllPendingJobs(vararg types: String): Map<String, Job?> {
        return jobDatabase.getAllJobs(*types)
    }

    override fun getAttachmentUploadJob(attachmentID: Long): AttachmentUploadJob? {
        return jobDatabase.getAttachmentUploadJob(attachmentID)
    }

    override fun getMessageSendJob(messageSendJobID: String): MessageSendJob? {
        return jobDatabase.getMessageSendJob(messageSendJobID)
    }

    override fun getMessageReceiveJob(messageReceiveJobID: String): MessageReceiveJob? {
        return jobDatabase.getMessageReceiveJob(messageReceiveJobID)
    }

    override fun getGroupAvatarDownloadJob(server: String, room: String, imageId: String?): GroupAvatarDownloadJob? {
        return jobDatabase.getGroupAvatarDownloadJob(server, room, imageId)
    }

    override fun resumeMessageSendJobIfNeeded(messageSendJobID: String) {
        val job = jobDatabase.getMessageSendJob(messageSendJobID) ?: return
        JobQueue.shared.resumePendingSendMessage(job)
    }

    override fun isJobCanceled(job: Job): Boolean {
        return jobDatabase.isJobCanceled(job)
    }

    override fun cancelPendingMessageSendJobs(threadID: Long) {
        val jobDb = jobDatabase
        jobDb.cancelPendingMessageSendJobs(threadID)
    }

    override fun getAuthToken(room: String, server: String): String? {
        val id = "$server.$room"
        return lokiAPIDatabase.getAuthToken(id)
    }

    override fun conversationInConfig(publicKey: String?, groupPublicKey: String?, openGroupId: String?, visibleOnly: Boolean): Boolean {
        return configFactory.conversationInConfig(publicKey, groupPublicKey, openGroupId, visibleOnly)
    }

    override fun canPerformConfigChange(variant: String, publicKey: String, changeTimestampMs: Long): Boolean {
        return configFactory.canPerformChange(variant, publicKey, changeTimestampMs)
    }

    override fun isCheckingCommunityRequests(): Boolean {
        return configFactory.withUserConfigs { it.userProfile.getCommunityMessageRequests() }
    }

    override fun clearUserPic(clearConfig: Boolean) {
        val userPublicKey = getUserPublicKey() ?: return Log.w(TAG, "No user public key when trying to clear user pic")
        val recipient = Recipient.from(context, fromSerialized(userPublicKey), false)

        // Clear details related to the user's profile picture
        preferences.setProfileKey(null)
        ProfileKeyUtil.setEncodedProfileKey(context, null)
        recipientDatabase.setProfileAvatar(recipient, null)
        preferences.setProfileAvatarId(0)
        preferences.setProfilePictureURL(null)

        Recipient.removeCached(fromSerialized(userPublicKey))
        if (clearConfig) {
            configFactory.withMutableUserConfigs {
                it.userProfile.setPic(UserPic.DEFAULT)
            }
        }
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
        if (threadId.toInt() < 0) { return null }
        val database = databaseHelper.readableDatabase
        return database.get(LokiThreadDatabase.publicChatTable, "${LokiThreadDatabase.threadID} = ?", arrayOf( threadId.toString() )) { cursor ->
            val publicChatAsJson = cursor.getString(LokiThreadDatabase.publicChat)
            OpenGroup.fromJSON(publicChatAsJson)
        }
    }

    override fun getOpenGroupPublicKey(server: String): String? {
        return lokiAPIDatabase.getOpenGroupPublicKey(server)
    }

    override fun setOpenGroupPublicKey(server: String, newValue: String) {
        lokiAPIDatabase.setOpenGroupPublicKey(server, newValue)
    }

    override fun getLastMessageServerID(room: String, server: String): Long? {
        return lokiAPIDatabase.getLastMessageServerID(room, server)
    }

    override fun setLastMessageServerID(room: String, server: String, newValue: Long) {
        lokiAPIDatabase.setLastMessageServerID(room, server, newValue)
    }

    override fun removeLastMessageServerID(room: String, server: String) {
        lokiAPIDatabase.removeLastMessageServerID(room, server)
    }

    override fun getLastDeletionServerID(room: String, server: String): Long? {
        return lokiAPIDatabase.getLastDeletionServerID(room, server)
    }

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

    override fun getOpenGroup(room: String, server: String): OpenGroup? {
        return getAllOpenGroups().values.firstOrNull { it.server == server && it.room == room }
    }

    override fun setGroupMemberRoles(members: List<GroupMember>) {
        groupMemberDatabase.setGroupMembers(members)
    }

    override fun isDuplicateMessage(timestamp: Long): Boolean {
        return getReceivedMessageTimestamps().contains(timestamp)
    }

    override fun updateTitle(groupID: String, newValue: String) {
        groupDatabase.updateTitle(groupID, newValue)
    }

    override fun updateProfilePicture(groupID: String, newValue: ByteArray) {
        groupDatabase.updateProfilePicture(groupID, newValue)
    }

    override fun removeProfilePicture(groupID: String) {
        groupDatabase.removeProfilePicture(groupID)
    }

    override fun hasDownloadedProfilePicture(groupID: String): Boolean {
        return groupDatabase.hasDownloadedProfilePicture(groupID)
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

    override fun getMessageIdInDatabase(timestamp: Long, author: String): Pair<Long, Boolean>? {
        val database = mmsSmsDatabase
        val address = fromSerialized(author)
        return database.getMessageFor(timestamp, address)?.run { getId() to isMms }
    }

    override fun getMessageType(timestamp: Long, author: String): MessageType? {
        val address = fromSerialized(author)
        return mmsSmsDatabase.getMessageFor(timestamp, address)?.individualRecipient?.getType()
    }

    override fun updateSentTimestamp(
        messageID: Long,
        isMms: Boolean,
        openGroupSentTimestamp: Long,
        threadId: Long
    ) {
        if (isMms) {
            val mmsDb = mmsDatabase
            mmsDb.updateSentTimestamp(messageID, openGroupSentTimestamp, threadId)
        } else {
            val smsDb = smsDatabase
            smsDb.updateSentTimestamp(messageID, openGroupSentTimestamp, threadId)
        }
    }

    override fun markAsSent(timestamp: Long, author: String) {
        val database = mmsSmsDatabase
        val messageRecord = database.getSentMessageFor(timestamp, author)
        if (messageRecord == null) {
            Log.w(TAG, "Failed to retrieve local message record in Storage.markAsSent - aborting.")
            return
        }

        if (messageRecord.isMms) {
            mmsDatabase.markAsSent(messageRecord.getId(), true)
        } else {
            smsDatabase.markAsSent(messageRecord.getId(), true)
        }
    }

    // Method that marks a message as sent in Communities (only!) - where the server modifies the
    // message timestamp and as such we cannot use that to identify the local message.
    override fun markAsSentToCommunity(threadId: Long, messageID: Long) {
        val database = mmsSmsDatabase
        val message = database.getLastSentMessageRecordFromSender(threadId, preferences.getLocalNumber())

        // Ensure we can find the local message..
        if (message == null) {
            Log.w(TAG, "Could not find local message in Storage.markAsSentToCommunity - aborting.")
            return
        }

        // ..and mark as sent if found.
        if (message.isMms) {
            mmsDatabase.markAsSent(message.getId(), true)
        } else {
            smsDatabase.markAsSent(message.getId(), true)
        }
    }

    override fun markAsSyncing(timestamp: Long, author: String) {
        mmsSmsDatabase
            .getMessageFor(timestamp, author)
            ?.run { getMmsDatabaseElseSms(isMms).markAsSyncing(id) }
    }

    private fun getMmsDatabaseElseSms(isMms: Boolean) =
        if (isMms) mmsDatabase
        else smsDatabase

    override fun markAsResyncing(timestamp: Long, author: String) {
        mmsSmsDatabase
            .getMessageFor(timestamp, author)
            ?.run { getMmsDatabaseElseSms(isMms).markAsResyncing(id) }
    }

    override fun markAsSending(timestamp: Long, author: String) {
        val database = mmsSmsDatabase
        val messageRecord = database.getMessageFor(timestamp, author) ?: return
        if (messageRecord.isMms) {
            val mmsDatabase = mmsDatabase
            mmsDatabase.markAsSending(messageRecord.getId())
        } else {
            val smsDatabase = smsDatabase
            smsDatabase.markAsSending(messageRecord.getId())
            messageRecord.isPending
        }
    }

    override fun markUnidentified(timestamp: Long, author: String) {
        val database = mmsSmsDatabase
        val messageRecord = database.getMessageFor(timestamp, author)
        if (messageRecord == null) {
            Log.w(TAG, "Could not identify message with timestamp: $timestamp from author: $author")
            return
        }
        if (messageRecord.isMms) {
            val mmsDatabase = mmsDatabase
            mmsDatabase.markUnidentified(messageRecord.getId(), true)
        } else {
            val smsDatabase = smsDatabase
            smsDatabase.markUnidentified(messageRecord.getId(), true)
        }
    }

    // Method that marks a message as unidentified in Communities (only!) - where the server
    // modifies the message timestamp and as such we cannot use that to identify the local message.
    override fun markUnidentifiedInCommunity(threadId: Long, messageId: Long) {
        val database = mmsSmsDatabase
        val message = database.getLastSentMessageRecordFromSender(threadId, preferences.getLocalNumber())

        // Check to ensure the message exists
        if (message == null) {
            Log.w(TAG, "Could not find local message in Storage.markUnidentifiedInCommunity - aborting.")
            return
        }

        // Mark it as unidentified if we found the message successfully
        if (message.isMms) {
            mmsDatabase.markUnidentified(message.getId(), true)
        } else {
            smsDatabase.markUnidentified(message.getId(), true)
        }
    }

    override fun markAsSentFailed(timestamp: Long, author: String, error: Exception) {
        val database = mmsSmsDatabase
        val messageRecord = database.getMessageFor(timestamp, author) ?: return
        if (messageRecord.isMms) {
            val mmsDatabase = mmsDatabase
            mmsDatabase.markAsSentFailed(messageRecord.getId())
        } else {
            val smsDatabase = smsDatabase
            smsDatabase.markAsSentFailed(messageRecord.getId())
        }
        if (error.localizedMessage != null) {
            val message: String
            if (error is OnionRequestAPI.HTTPRequestFailedAtDestinationException && error.statusCode == 429) {
                message = "429: Rate limited."
            } else {
                message = error.localizedMessage!!
            }
            lokiMessageDatabase.setErrorMessage(messageRecord.getId(), message)
        } else {
            lokiMessageDatabase.setErrorMessage(messageRecord.getId(), error.javaClass.simpleName)
        }
    }

    override fun markAsSyncFailed(timestamp: Long, author: String, error: Exception) {
        val database = mmsSmsDatabase
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
            lokiMessageDatabase.setErrorMessage(messageRecord.getId(), message)
        } else {
            lokiMessageDatabase.setErrorMessage(messageRecord.getId(), error.javaClass.simpleName)
        }
    }

    override fun clearErrorMessage(messageID: Long) {
        val db = lokiMessageDatabase
        db.clearErrorMessage(messageID)
    }

    override fun setMessageServerHash(messageID: Long, mms: Boolean, serverHash: String) {
        lokiMessageDatabase.setMessageServerHash(messageID, mms, serverHash)
    }

    override fun getGroup(groupID: String): GroupRecord? {
        val group = groupDatabase.getGroup(groupID)
        return if (group.isPresent) { group.get() } else null
    }

    override fun createGroup(groupId: String, title: String?, members: List<Address>, avatar: SignalServiceAttachmentPointer?, relay: String?, admins: List<Address>, formationTimestamp: Long) {
        groupDatabase.create(groupId, title, members, avatar, relay, admins, formationTimestamp)
    }

    override fun createInitialConfigGroup(groupPublicKey: String, name: String, members: Map<String, Boolean>, formationTimestamp: Long, encryptionKeyPair: ECKeyPair, expirationTimer: Int) {
        configFactory.withMutableUserConfigs {
            val volatiles = it.convoInfoVolatile
            val userGroups = it.userGroups
            if (volatiles.getLegacyClosedGroup(groupPublicKey) != null && userGroups.getLegacyGroupInfo(groupPublicKey) != null) {
                return@withMutableUserConfigs
            }

            val groupVolatileConfig = volatiles.getOrConstructLegacyGroup(groupPublicKey)
            groupVolatileConfig.lastRead = formationTimestamp
            volatiles.set(groupVolatileConfig)
            val groupInfo = GroupInfo.LegacyGroupInfo(
                accountId = groupPublicKey,
                name = name,
                members = members,
                priority = PRIORITY_VISIBLE,
                encPubKey = (encryptionKeyPair.publicKey as DjbECPublicKey).publicKey,  // 'serialize()' inserts an extra byte
                encSecKey = encryptionKeyPair.privateKey.serialize(),
                disappearingTimer = expirationTimer.toLong(),
                joinedAt = (formationTimestamp / 1000L)
            )
            // shouldn't exist, don't use getOrConstruct + copy
            userGroups.set(groupInfo)
        }
    }

    override fun updateGroupConfig(groupPublicKey: String) {
        val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
        val groupAddress = fromSerialized(groupID)
        val existingGroup = getGroup(groupID)
            ?: return Log.w("Loki-DBG", "No existing group for ${groupPublicKey.take(4)}} when updating group config")
        configFactory.withMutableUserConfigs {
            val userGroups = it.userGroups
            if (!existingGroup.isActive) {
                userGroups.eraseLegacyGroup(groupPublicKey)
                return@withMutableUserConfigs
            }
            val name = existingGroup.title
            val admins = existingGroup.admins.map { it.serialize() }
            val members = existingGroup.members.map { it.serialize() }
            val membersMap = GroupUtil.createConfigMemberMap(admins = admins, members = members)
            val latestKeyPair = getLatestClosedGroupEncryptionKeyPair(groupPublicKey)
                ?: return@withMutableUserConfigs Log.w("Loki-DBG", "No latest closed group encryption key pair for ${groupPublicKey.take(4)}} when updating group config")

            val threadID = getThreadId(groupAddress) ?: return@withMutableUserConfigs
            val groupInfo = userGroups.getOrConstructLegacyGroupInfo(groupPublicKey).copy(
                name = name,
                members = membersMap,
                encPubKey = (latestKeyPair.publicKey as DjbECPublicKey).publicKey,  // 'serialize()' inserts an extra byte
                encSecKey = latestKeyPair.privateKey.serialize(),
                priority = if (isPinned(threadID)) PRIORITY_PINNED else PRIORITY_VISIBLE,
                disappearingTimer = getExpirationConfiguration(threadID)?.expiryMode?.expirySeconds ?: 0L,
                joinedAt = (existingGroup.formationTimestamp / 1000L)
            )
            userGroups.set(groupInfo)
        }
    }

    override fun isGroupActive(groupPublicKey: String): Boolean {
        return groupDatabase.getGroup(GroupUtil.doubleEncodeGroupID(groupPublicKey)).orNull()?.isActive == true
    }

    override fun setActive(groupID: String, value: Boolean) {
        groupDatabase.setActive(groupID, value)
    }

    override fun getZombieMembers(groupID: String): Set<String> {
        return groupDatabase.getGroupZombieMembers(groupID).map { it.address.serialize() }.toHashSet()
    }

    override fun removeMember(groupID: String, member: Address) {
        groupDatabase.removeMember(groupID, member)
    }

    override fun updateMembers(groupID: String, members: List<Address>) {
        groupDatabase.updateMembers(groupID, members)
    }

    override fun setZombieMembers(groupID: String, members: List<Address>) {
        groupDatabase.updateZombieMembers(groupID, members)
    }

    override fun insertIncomingInfoMessage(context: Context, senderPublicKey: String, groupID: String, type: SignalServiceGroup.Type, name: String, members: Collection<String>, admins: Collection<String>, sentTimestamp: Long): Long? {
        val group = SignalServiceGroup(type, GroupUtil.getDecodedGroupIDAsData(groupID), SignalServiceGroup.GroupType.SIGNAL, name, members.toList(), null, admins.toList())
        val m = IncomingTextMessage(fromSerialized(senderPublicKey), 1, sentTimestamp, "", Optional.of(group), 0, 0, true, false)
        val updateData = UpdateMessageData.buildGroupUpdate(type, name, members)?.toJSON()
        val infoMessage = IncomingGroupMessage(m, updateData, true)
        val smsDB = smsDatabase
        return smsDB.insertMessageInbox(infoMessage,  true).orNull().messageId
    }

    override fun updateInfoMessage(context: Context, messageId: Long, groupID: String, type: SignalServiceGroup.Type, name: String, members: Collection<String>) {
        val mmsDB = mmsDatabase
        val updateData = UpdateMessageData.buildGroupUpdate(type, name, members)?.toJSON()
        mmsDB.updateInfoMessage(messageId, updateData)
    }

    override fun insertOutgoingInfoMessage(context: Context, groupID: String, type: SignalServiceGroup.Type, name: String, members: Collection<String>, admins: Collection<String>, threadID: Long, sentTimestamp: Long): Long? {
        val userPublicKey = getUserPublicKey()!!
        val recipient = Recipient.from(context, fromSerialized(groupID), false)
        val updateData = UpdateMessageData.buildGroupUpdate(type, name, members)?.toJSON() ?: ""
        val infoMessage = OutgoingGroupMediaMessage(recipient, updateData, groupID, null, sentTimestamp, 0, 0, true, null, listOf(), listOf())
        val mmsDB = mmsDatabase
        val mmsSmsDB = mmsSmsDatabase
        if (mmsSmsDB.getMessageFor(sentTimestamp, userPublicKey) != null) {
            Log.w(TAG, "Bailing from insertOutgoingInfoMessage because we believe the message has already been sent!")
            return null
        }
        val infoMessageID = mmsDB.insertMessageOutbox(infoMessage, threadID, false, null, runThreadUpdate = true)
        mmsDB.markAsSent(infoMessageID, true)
        return infoMessageID
    }

    override fun isLegacyClosedGroup(publicKey: String): Boolean {
        return lokiAPIDatabase.isClosedGroup(publicKey)
    }

    override fun getClosedGroupEncryptionKeyPairs(groupPublicKey: String): MutableList<ECKeyPair> {
        return lokiAPIDatabase.getClosedGroupEncryptionKeyPairs(groupPublicKey).toMutableList()
    }

    override fun getLatestClosedGroupEncryptionKeyPair(groupPublicKey: String): ECKeyPair? {
        return lokiAPIDatabase.getLatestClosedGroupEncryptionKeyPair(groupPublicKey)
    }

    override fun getAllClosedGroupPublicKeys(): Set<String> {
        return lokiAPIDatabase.getAllClosedGroupPublicKeys()
    }

    override fun getAllActiveClosedGroupPublicKeys(): Set<String> {
        return lokiAPIDatabase.getAllClosedGroupPublicKeys().filter {
            getGroup(GroupUtil.doubleEncodeGroupID(it))?.isActive == true
        }.toSet()
    }

    override fun addClosedGroupPublicKey(groupPublicKey: String) {
        lokiAPIDatabase.addClosedGroupPublicKey(groupPublicKey)
    }

    override fun removeClosedGroupPublicKey(groupPublicKey: String) {
        lokiAPIDatabase.removeClosedGroupPublicKey(groupPublicKey)
    }

    override fun addClosedGroupEncryptionKeyPair(encryptionKeyPair: ECKeyPair, groupPublicKey: String, timestamp: Long) {
        lokiAPIDatabase.addClosedGroupEncryptionKeyPair(encryptionKeyPair, groupPublicKey, timestamp)
    }

    override fun removeAllClosedGroupEncryptionKeyPairs(groupPublicKey: String) {
        lokiAPIDatabase.removeAllClosedGroupEncryptionKeyPairs(groupPublicKey)
    }

    override fun removeClosedGroupThread(threadID: Long) {
        threadDatabase.deleteConversation(threadID)
    }

    override fun updateFormationTimestamp(groupID: String, formationTimestamp: Long) {
        groupDatabase
            .updateFormationTimestamp(groupID, formationTimestamp)
    }

    override fun updateTimestampUpdated(groupID: String, updatedTimestamp: Long) {
        groupDatabase
            .updateTimestampUpdated(groupID, updatedTimestamp)
    }

    /**
     * For new closed groups
     */
    override fun getMembers(groupPublicKey: String): List<LibSessionGroupMember> =
        configFactory.withGroupConfigs(AccountId(groupPublicKey)) {
            it.groupMembers.all()
        }

    override fun getClosedGroupDisplayInfo(groupAccountId: String): GroupDisplayInfo? {
        val groupIsAdmin = configFactory.getClosedGroup(AccountId(groupAccountId))?.hasAdminKey() ?: return null

        return configFactory.withGroupConfigs(AccountId(groupAccountId)) { configs ->
            val info = configs.groupInfo
            GroupDisplayInfo(
                id = info.id(),
                name = info.getName(),
                profilePic = info.getProfilePic(),
                expiryTimer = info.getExpiryTimer(),
                destroyed = false,
                created = info.getCreated(),
                description = info.getDescription(),
                isUserAdmin = groupIsAdmin
            )
        }
    }

    override fun insertGroupInfoChange(message: GroupUpdated, closedGroup: AccountId): Long? {
        val sentTimestamp = message.sentTimestamp ?: clock.currentTimeMills()
        val senderPublicKey = message.sender
        val groupName = configFactory.withGroupConfigs(closedGroup) { it.groupInfo.getName() }
            ?: configFactory.getClosedGroup(closedGroup)?.name

        val updateData = UpdateMessageData.buildGroupUpdate(message, groupName.orEmpty()) ?: return null

        return insertUpdateControlMessage(updateData, sentTimestamp, senderPublicKey, closedGroup)
    }

    override fun insertGroupInfoLeaving(closedGroup: AccountId): Long? {
        val sentTimestamp = clock.currentTimeMills()
        val senderPublicKey = getUserPublicKey() ?: return null
        val updateData = UpdateMessageData.buildGroupLeaveUpdate(UpdateMessageData.Kind.GroupLeaving)

        return insertUpdateControlMessage(updateData, sentTimestamp, senderPublicKey, closedGroup)
    }

    override fun updateGroupInfoChange(messageId: Long, newType: UpdateMessageData.Kind) {
        val mmsDB = mmsDatabase
        val newMessage = UpdateMessageData.buildGroupLeaveUpdate(newType)
        mmsDB.updateInfoMessage(messageId, newMessage.toJSON())
    }

    override fun insertGroupInviteControlMessage(sentTimestamp: Long, senderPublicKey: String, closedGroup: AccountId, groupName: String): Long? {
        val updateData = UpdateMessageData(UpdateMessageData.Kind.GroupInvitation(closedGroup.hexString, senderPublicKey, groupName))
        return insertUpdateControlMessage(updateData, sentTimestamp, senderPublicKey, closedGroup)
    }

    private fun insertUpdateControlMessage(updateData: UpdateMessageData, sentTimestamp: Long, senderPublicKey: String?, closedGroup: AccountId): Long? {
        val userPublicKey = getUserPublicKey()!!
        val recipient = Recipient.from(context, fromSerialized(closedGroup.hexString), false)
        val threadDb = threadDatabase
        val threadID = threadDb.getThreadIdIfExistsFor(recipient)
        val expirationConfig = getExpirationConfiguration(threadID)
        val expiryMode = expirationConfig?.expiryMode
        val expiresInMillis = expiryMode?.expiryMillis ?: 0
        val expireStartedAt = if (expiryMode is ExpiryMode.AfterSend) sentTimestamp else 0
        val inviteJson = updateData.toJSON()


        if (senderPublicKey == null || senderPublicKey == userPublicKey) {
            val infoMessage = OutgoingGroupMediaMessage(
                recipient,
                inviteJson,
                closedGroup.hexString,
                null,
                sentTimestamp,
                expiresInMillis,
                expireStartedAt,
                true,
                null,
                listOf(),
                listOf()
            )
            val mmsDB = mmsDatabase
            val mmsSmsDB = mmsSmsDatabase
            // check for conflict here, not returning duplicate in case it's different
            if (mmsSmsDB.getMessageFor(sentTimestamp, userPublicKey) != null) return null
            val infoMessageID = mmsDB.insertMessageOutbox(infoMessage, threadID, false, null, runThreadUpdate = true)
            mmsDB.markAsSent(infoMessageID, true)
            return infoMessageID
        } else {
            val group = SignalServiceGroup(Hex.fromStringCondensed(closedGroup.hexString), SignalServiceGroup.GroupType.SIGNAL)
            val m = IncomingTextMessage(fromSerialized(senderPublicKey), 1, sentTimestamp, "", Optional.of(group), expiresInMillis, expireStartedAt, true, false)
            val infoMessage = IncomingGroupMessage(m, inviteJson, true)
            val smsDB = smsDatabase
            val insertResult = smsDB.insertMessageInbox(infoMessage,  true)
            return insertResult.orNull()?.messageId
        }
    }

    override fun setServerCapabilities(server: String, capabilities: List<String>) {
        return lokiAPIDatabase.setServerCapabilities(server, capabilities)
    }

    override fun getServerCapabilities(server: String): List<String> {
        return lokiAPIDatabase.getServerCapabilities(server)
    }

    override fun getAllOpenGroups(): Map<Long, OpenGroup> {
        return lokiThreadDatabase.getAllOpenGroups()
    }

    override fun updateOpenGroup(openGroup: OpenGroup) {
        OpenGroupManager.updateOpenGroup(openGroup, context)
    }

    override fun getAllGroups(includeInactive: Boolean): List<GroupRecord> {
        return groupDatabase.getAllGroups(includeInactive)
    }

    override fun addOpenGroup(urlAsString: String): OpenGroupApi.RoomInfo? {
        return OpenGroupManager.addOpenGroup(urlAsString, context)
    }

    override fun onOpenGroupAdded(server: String, room: String) {
        OpenGroupManager.restartPollerForServer(server.removeSuffix("/"))
        configFactory.withMutableUserConfigs { configs ->
            val groups = configs.userGroups
            val volatileConfig = configs.convoInfoVolatile
            val openGroup = getOpenGroup(room, server) ?: return@withMutableUserConfigs
            val (infoServer, infoRoom, pubKey) = BaseCommunityInfo.parseFullUrl(openGroup.joinURL) ?: return@withMutableUserConfigs
            val pubKeyHex = Hex.toStringCondensed(pubKey)
            val communityInfo = groups.getOrConstructCommunityInfo(infoServer, infoRoom, pubKeyHex)
            groups.set(communityInfo)
            val volatile = volatileConfig.getOrConstructCommunity(infoServer, infoRoom, pubKey)
            if (volatile.lastRead != 0L) {
                val threadId = getThreadId(openGroup) ?: return@withMutableUserConfigs
                markConversationAsRead(threadId, volatile.lastRead, force = true)
            }
            volatileConfig.set(volatile)
        }
    }

    override fun hasBackgroundGroupAddJob(groupJoinUrl: String): Boolean {
        return jobDatabase.hasBackgroundGroupAddJob(groupJoinUrl)
    }

    override fun setProfileSharing(address: Address, value: Boolean) {
        val recipient = Recipient.from(context, address, false)
        recipientDatabase.setProfileSharing(recipient, value)
    }

    override fun getOrCreateThreadIdFor(address: Address): Long {
        val recipient = Recipient.from(context, address, false)
        return threadDatabase.getOrCreateThreadIdFor(recipient)
    }

    override fun getThreadIdFor(publicKey: String, groupPublicKey: String?, openGroupID: String?, createThread: Boolean): Long? {
        val database = threadDatabase
        return if (!openGroupID.isNullOrEmpty()) {
            val recipient = Recipient.from(context, fromSerialized(GroupUtil.getEncodedOpenGroupID(openGroupID.toByteArray())), false)
            database.getThreadIdIfExistsFor(recipient).let { if (it == -1L) null else it }
        } else if (!groupPublicKey.isNullOrEmpty() && !groupPublicKey.startsWith(IdPrefix.GROUP.value)) {
            val recipient = Recipient.from(context, fromSerialized(GroupUtil.doubleEncodeGroupID(groupPublicKey)), false)
            if (createThread) database.getOrCreateThreadIdFor(recipient)
            else database.getThreadIdIfExistsFor(recipient).let { if (it == -1L) null else it }
        } else if (!groupPublicKey.isNullOrEmpty()) {
            val recipient = Recipient.from(context, fromSerialized(groupPublicKey), false)
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
        val threadID = threadDatabase.getThreadIdIfExistsFor(recipient)
        return if (threadID < 0) null else threadID
    }

    override fun getThreadIdForMms(mmsId: Long): Long {
        val mmsDb = mmsDatabase
        val cursor = mmsDb.getMessage(mmsId)
        val reader = mmsDb.readerFor(cursor)
        val threadId = reader.next?.threadId
        cursor.close()
        return threadId ?: -1
    }

    override fun getContactWithAccountID(accountID: String): Contact? {
        return sessionContactDatabase.getContactWithAccountID(accountID)
    }

    override fun getAllContacts(): Set<Contact> {
        return sessionContactDatabase.getAllContacts()
    }

    override fun setContact(contact: Contact) {
        sessionContactDatabase.setContact(contact)
        val address = fromSerialized(contact.accountID)
        if (!getRecipientApproved(address)) return
        val recipientHash = profileManager.contactUpdatedInternal(contact)
        val recipient = Recipient.from(context, address, false)
        setRecipientHash(recipient, recipientHash)
    }

    override fun getRecipientForThread(threadId: Long): Recipient? {
        return threadDatabase.getRecipientForThreadId(threadId)
    }

    override fun getRecipientSettings(address: Address): Recipient.RecipientSettings? {
        return recipientDatabase.getRecipientSettings(address).orNull()
    }

    override fun hasAutoDownloadFlagBeenSet(recipient: Recipient): Boolean {
        return recipientDatabase.isAutoDownloadFlagSet(recipient)
    }

    override fun addLibSessionContacts(contacts: List<LibSessionContact>, timestamp: Long) {
        val mappingDb = blindedIdMappingDatabase
        val moreContacts = contacts.filter { contact ->
            val id = AccountId(contact.id)
            id.prefix?.isBlinded() == false || mappingDb.getBlindedIdMapping(contact.id).none { it.accountId != null }
        }
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
                getThreadId(fromSerialized(contact.id))?.let(::deleteConversation)
            } else {
                (
                    getThreadId(address) ?: getOrCreateThreadIdFor(address).also {
                        setThreadDate(it, 0)
                    }
                ).also { setPinned(it, contact.priority == PRIORITY_PINNED) }
            }
            getThreadId(recipient)?.let {
                setExpirationConfiguration(
                    getExpirationConfiguration(it)?.takeIf { it.updatedTimestampMs > timestamp }
                        ?: ExpirationConfiguration(it, contact.expiryMode, timestamp)
                )
            }
            setRecipientHash(recipient, contact.hashCode().toString())
        }

        // if we have contacts locally but that are missing from the config, remove their corresponding thread
        val  removedContacts = getAllContacts().filter { localContact ->
            moreContacts.firstOrNull {
                it.id == localContact.accountID
            } == null
        }
        removedContacts.forEach {
            getThreadId(fromSerialized(it.accountID))?.let(::deleteConversation)
        }
    }

    override fun addContacts(contacts: List<ConfigurationMessage.Contact>) {
        val recipientDatabase = recipientDatabase
        val threadDatabase = threadDatabase
        val mappingDb = blindedIdMappingDatabase
        val moreContacts = contacts.filter { contact ->
            val id = AccountId(contact.publicKey)
            id.prefix != IdPrefix.BLINDED || mappingDb.getBlindedIdMapping(contact.publicKey).none { it.accountId != null }
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

    override fun shouldAutoDownloadAttachments(recipient: Recipient): Boolean {
        return recipient.autoDownloadAttachments
    }

    override fun setAutoDownloadAttachments(
        recipient: Recipient,
        shouldAutoDownloadAttachments: Boolean
    ) {
        val recipientDb = recipientDatabase
        recipientDb.setAutoDownloadAttachments(recipient, shouldAutoDownloadAttachments)
    }

    override fun setRecipientHash(recipient: Recipient, recipientHash: String?) {
        val recipientDb = recipientDatabase
        recipientDb.setRecipientHash(recipient, recipientHash)
    }

    override fun getLastUpdated(threadID: Long): Long {
        val threadDB = threadDatabase
        return threadDB.getLastUpdated(threadID)
    }

    override fun trimThread(threadID: Long, threadLimit: Int) {
        val threadDB = threadDatabase
        threadDB.trimThread(threadID, threadLimit)
    }

    override fun trimThreadBefore(threadID: Long, timestamp: Long) {
        val threadDB = threadDatabase
        threadDB.trimThreadBefore(threadID, timestamp)
    }

    override fun getMessageCount(threadID: Long): Long {
        val mmsSmsDb = mmsSmsDatabase
        return mmsSmsDb.getConversationCount(threadID)
    }

    override fun setPinned(threadID: Long, isPinned: Boolean) {
        val threadDB = threadDatabase
        threadDB.setPinned(threadID, isPinned)
        val threadRecipient = getRecipientForThread(threadID) ?: return
        configFactory.withMutableUserConfigs { configs ->
            if (threadRecipient.isLocalNumber) {
                configs.userProfile.setNtsPriority(if (isPinned) PRIORITY_PINNED else PRIORITY_VISIBLE)
            } else if (threadRecipient.isContactRecipient) {
                configs.contacts.upsertContact(threadRecipient.address.serialize()) {
                    priority = if (isPinned) PRIORITY_PINNED else PRIORITY_VISIBLE
                }
            } else if (threadRecipient.isGroupRecipient) {
                when {
                    threadRecipient.isLegacyClosedGroupRecipient -> {
                        threadRecipient.address.serialize()
                            .let(GroupUtil::doubleDecodeGroupId)
                            .let(configs.userGroups::getOrConstructLegacyGroupInfo)
                            .copy(priority = if (isPinned) PRIORITY_PINNED else PRIORITY_VISIBLE)
                            .let(configs.userGroups::set)
                    }

                    threadRecipient.isClosedGroupV2Recipient -> {
                        val newGroupInfo = configs.userGroups
                            .getOrConstructClosedGroup(threadRecipient.address.serialize())
                            .copy(priority = if (isPinned) PRIORITY_PINNED else PRIORITY_VISIBLE)
                        configs.userGroups.set(newGroupInfo)
                    }

                    threadRecipient.isCommunityRecipient -> {
                        val openGroup = getOpenGroup(threadID) ?: return@withMutableUserConfigs
                        val (baseUrl, room, pubKeyHex) = BaseCommunityInfo.parseFullUrl(openGroup.joinURL)
                            ?: return@withMutableUserConfigs
                        val newGroupInfo = configs.userGroups.getOrConstructCommunityInfo(
                            baseUrl,
                            room,
                            Hex.toStringCondensed(pubKeyHex)
                        ).copy(priority = if (isPinned) PRIORITY_PINNED else PRIORITY_VISIBLE)
                        configs.userGroups.set(newGroupInfo)
                    }
                }
            }
        }
    }

    override fun isPinned(threadID: Long): Boolean {
        val threadDB = threadDatabase
        return threadDB.isPinned(threadID)
    }

    override fun setThreadDate(threadId: Long, newDate: Long) {
        val threadDb = threadDatabase
        threadDb.setDate(threadId, newDate)
    }

    override fun getLastLegacyRecipient(threadRecipient: String): String? =
        lokiAPIDatabase.getLastLegacySenderAddress(threadRecipient)

    override fun setLastLegacyRecipient(threadRecipient: String, senderRecipient: String?) {
        lokiAPIDatabase.setLastLegacySenderAddress(threadRecipient, senderRecipient)
    }

    override fun deleteConversation(threadID: Long) {
        val threadDB = threadDatabase
        val groupDB = groupDatabase
        threadDB.deleteConversation(threadID)

        val recipient = getRecipientForThread(threadID)
        if (recipient == null) {
            Log.w(TAG, "Got null recipient when deleting conversation - aborting.");
            return
        }

        // There is nothing further we need to do if this is a 1-on-1 conversation, and it's not
        // possible to delete communities in this manner so bail.
        if (recipient.isContactRecipient || recipient.isCommunityRecipient) return

        // If we get here then this is a closed group conversation (i.e., recipient.isClosedGroupRecipient)
        configFactory.withMutableUserConfigs { configs ->
            val volatile = configs.convoInfoVolatile
            val groups = configs.userGroups
            val groupID = recipient.address.toGroupString()
            val closedGroup = getGroup(groupID)
            val groupPublicKey = GroupUtil.doubleDecodeGroupId(recipient.address.serialize())
            if (closedGroup != null) {
                groupDB.delete(groupID)
                volatile.eraseLegacyClosedGroup(groupPublicKey)
                groups.eraseLegacyGroup(groupPublicKey)
            } else {
                Log.w("Loki-DBG", "Failed to find a closed group for ${groupPublicKey.take(4)}")
            }
        }
    }

    override fun clearMessages(threadID: Long, fromUser: Address?): Boolean {
        val threadDb = threadDatabase
        if (fromUser == null) {
            // this deletes all *from* thread, not deleting the actual thread
            smsDatabase.deleteThread(threadID)
            mmsDatabase.deleteThread(threadID) // threadDB update called from within
        } else {
            // this deletes all *from* thread, not deleting the actual thread
            smsDatabase.deleteMessagesFrom(threadID, fromUser.serialize())
            mmsDatabase.deleteMessagesFrom(threadID, fromUser.serialize())
            threadDb.update(threadID, false)
        }
        return true
    }

    override fun clearMedia(threadID: Long, fromUser: Address?): Boolean {
        mmsDatabase.deleteMediaFor(threadID, fromUser?.serialize())
        return true
    }

    override fun getAttachmentDataUri(attachmentId: AttachmentId): Uri {
        return PartAuthority.getAttachmentDataUri(attachmentId)
    }

    override fun getAttachmentThumbnailUri(attachmentId: AttachmentId): Uri {
        return PartAuthority.getAttachmentThumbnailUri(attachmentId)
    }

    override fun insertDataExtractionNotificationMessage(senderPublicKey: String, message: DataExtractionNotificationInfoMessage, sentTimestamp: Long) {
        val database = mmsDatabase
        val address = fromSerialized(senderPublicKey)
        val recipient = Recipient.from(context, address, false)

        if (recipient.isBlocked) return
        val threadId = getThreadId(recipient) ?: return
        val expirationConfig = getExpirationConfiguration(threadId)
        val expiryMode = expirationConfig?.expiryMode ?: ExpiryMode.NONE
        val expiresInMillis = expiryMode.expiryMillis
        val expireStartedAt = if (expiryMode is ExpiryMode.AfterSend) sentTimestamp else 0
        val mediaMessage = IncomingMediaMessage(
            address,
            sentTimestamp,
            -1,
            expiresInMillis,
            expireStartedAt,
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
        messageExpirationManager.maybeStartExpiration(sentTimestamp, senderPublicKey, expiryMode)
    }

    /**
     * This will create a control message used to indicate that a contact has accepted our message request
     */
    override fun insertMessageRequestResponseFromContact(response: MessageRequestResponse) {
        val userPublicKey = getUserPublicKey()
        val senderPublicKey = response.sender!!
        val recipientPublicKey = response.recipient!!

        if (
            userPublicKey == null
            || (userPublicKey != recipientPublicKey && userPublicKey != senderPublicKey)
            // this is true if it is a sync message
            || (userPublicKey == recipientPublicKey && userPublicKey == senderPublicKey)
        ) return

        val recipientDb = recipientDatabase
        val threadDB = threadDatabase
        if (userPublicKey == senderPublicKey) {
            val requestRecipient = Recipient.from(context, fromSerialized(recipientPublicKey), false)
            recipientDb.setApproved(requestRecipient, true)
            val threadId = threadDB.getOrCreateThreadIdFor(requestRecipient)
            threadDB.setHasSent(threadId, true)
        } else {
            val mmsDb = mmsDatabase
            val smsDb = smsDatabase
            val sender = Recipient.from(context, fromSerialized(senderPublicKey), false)
            val threadId = getOrCreateThreadIdFor(sender.address)
            val profile = response.profile
            if (profile != null) {
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
            val mappingDb = blindedIdMappingDatabase
            val mappings = mutableMapOf<String, BlindedIdMapping>()
            threadDB.readerFor(threadDB.conversationList).use { reader ->
                while (reader.next != null) {
                    val recipient = reader.current.recipient
                    val address = recipient.address.serialize()
                    val blindedId = when {
                        recipient.isGroupRecipient -> null
                        recipient.isOpenGroupInboxRecipient -> GroupUtil.getDecodedOpenGroupInboxAccountId(address)
                        else -> address.takeIf { AccountId(it).prefix == IdPrefix.BLINDED }
                    } ?: continue
                    mappingDb.getBlindedIdMapping(blindedId).firstOrNull()?.let {
                        mappings[address] = it
                    }
                }
            }
            for (mapping in mappings) {
                if (!SodiumUtilities.accountId(senderPublicKey, mapping.value.blindedId, mapping.value.serverId)) {
                    continue
                }
                mappingDb.addBlindedIdMapping(mapping.value.copy(accountId = senderPublicKey))

                val blindedThreadId = threadDB.getOrCreateThreadIdFor(Recipient.from(context, fromSerialized(mapping.key), false))
                mmsDb.updateThreadId(blindedThreadId, threadId)
                smsDb.updateThreadId(blindedThreadId, threadId)
                threadDB.deleteConversation(blindedThreadId)
            }
            setRecipientApproved(sender, true)
            setRecipientApprovedMe(sender, true)

            // Also update the config about this contact
            configFactory.withMutableUserConfigs {
                it.contacts.upsertContact(sender.address.serialize()) {
                    approved = true
                    approvedMe = true
                }
            }

            val message = IncomingMediaMessage(
                sender.address,
                response.sentTimestamp!!,
                -1,
                0,
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

    /**
     * This will create a control message used to indicate that you have accepted a message request
     */
    override fun insertMessageRequestResponseFromYou(threadId: Long){
        val userPublicKey = getUserPublicKey() ?: return

        val mmsDb = mmsDatabase
        val message = IncomingMediaMessage(
            fromSerialized(userPublicKey),
            clock.currentTimeMills(),
            -1,
            0,
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
        mmsDb.insertSecureDecryptedMessageInbox(message, threadId, runThreadUpdate = false)
    }

    override fun getRecipientApproved(address: Address): Boolean {
        return address.isClosedGroupV2 || recipientDatabase.getApproved(address)
    }

    override fun setRecipientApproved(recipient: Recipient, approved: Boolean) {
        recipientDatabase.setApproved(recipient, approved)
        if (recipient.isLocalNumber || !recipient.isContactRecipient) return
        configFactory.withMutableUserConfigs {
            it.contacts.upsertContact(recipient.address.serialize()) {
                // if the contact wasn't approved before but is approved now, make sure it's visible
                if(approved && !this.approved) this.priority = PRIORITY_VISIBLE

                // update approval
                this.approved = approved
            }
        }
    }

    override fun setRecipientApprovedMe(recipient: Recipient, approvedMe: Boolean) {
        recipientDatabase.setApprovedMe(recipient, approvedMe)
        if (recipient.isLocalNumber || !recipient.isContactRecipient) return
        configFactory.withMutableUserConfigs {
            it.contacts.upsertContact(recipient.address.serialize()) {
                this.approvedMe = approvedMe
            }
        }
    }

    override fun insertCallMessage(senderPublicKey: String, callMessageType: CallMessageType, sentTimestamp: Long) {
        val database = smsDatabase
        val address = fromSerialized(senderPublicKey)
        val recipient = Recipient.from(context, address, false)
        val threadId = threadDatabase.getOrCreateThreadIdFor(recipient)
        val expirationConfig = getExpirationConfiguration(threadId)
        val expiryMode = expirationConfig?.expiryMode?.coerceSendToRead() ?: ExpiryMode.NONE
        val expiresInMillis = expiryMode.expiryMillis
        val expireStartedAt = if (expiryMode is ExpiryMode.AfterSend) sentTimestamp else 0
        val callMessage = IncomingTextMessage.fromCallInfo(callMessageType, address, Optional.absent(), sentTimestamp, expiresInMillis, expireStartedAt)
        database.insertCallMessage(callMessage)
        messageExpirationManager.maybeStartExpiration(sentTimestamp, senderPublicKey, expiryMode)
    }

    override fun conversationHasOutgoing(userPublicKey: String): Boolean {
        val database = threadDatabase
        val threadId = database.getThreadIdIfExistsFor(userPublicKey)

        if (threadId == -1L) return false

        return database.getLastSeenAndHasSent(threadId).second() ?: false
    }

    override fun getLastInboxMessageId(server: String): Long? {
        return lokiAPIDatabase.getLastInboxMessageId(server)
    }

    override fun setLastInboxMessageId(server: String, messageId: Long) {
        lokiAPIDatabase.setLastInboxMessageId(server, messageId)
    }

    override fun removeLastInboxMessageId(server: String) {
        lokiAPIDatabase.removeLastInboxMessageId(server)
    }

    override fun getLastOutboxMessageId(server: String): Long? {
        return lokiAPIDatabase.getLastOutboxMessageId(server)
    }

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
        val db = blindedIdMappingDatabase
        val mapping = db.getBlindedIdMapping(blindedId).firstOrNull() ?: BlindedIdMapping(blindedId, null, server, serverPublicKey)
        if (mapping.accountId != null) {
            return mapping
        }
        getAllContacts().forEach { contact ->
            val accountId = AccountId(contact.accountID)
            if (accountId.prefix == IdPrefix.STANDARD && SodiumUtilities.accountId(accountId.hexString, blindedId, serverPublicKey)) {
                val contactMapping = mapping.copy(accountId = accountId.hexString)
                db.addBlindedIdMapping(contactMapping)
                return contactMapping
            }
        }
        db.getBlindedIdMappingsExceptFor(server).forEach {
            if (SodiumUtilities.accountId(it.accountId!!, blindedId, serverPublicKey)) {
                val otherMapping = mapping.copy(accountId = it.accountId)
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
            // bail early is the message is marked as deleted
            val messagingDatabase: MessagingDatabase = if (isMms == true) mmsDatabase else smsDatabase
            if(messagingDatabase.getMessageRecord(localId)?.isDeleted == true) return

            MessageId(localId, isMms)
        } else if (timestamp != null && timestamp > 0) {
            val messageRecord = mmsSmsDatabase.getMessageForTimestamp(timestamp) ?: return
            if (messageRecord.isDeleted) return
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
        val database = reactionDatabase
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
        reactionDatabase.deleteMessageReactions(MessageId(messageId, mms))
    }

    override fun deleteReactions(messageIds: List<Long>, mms: Boolean) {
        reactionDatabase.deleteMessageReactions(
            messageIds.map { MessageId(it, mms) }
        )
    }

    override fun setBlocked(recipients: Iterable<Recipient>, isBlocked: Boolean, fromConfigUpdate: Boolean) {
        val recipientDb = recipientDatabase
        recipientDb.setBlocked(recipients, isBlocked)
        configFactory.withMutableUserConfigs { configs ->
            recipients.filter { it.isContactRecipient && !it.isLocalNumber }.forEach { recipient ->
                configs.contacts.upsertContact(recipient.address.serialize()) {
                    this.blocked = isBlocked
                }
            }
        }
    }

    override fun blockedContacts(): List<Recipient> {
        val recipientDb = recipientDatabase
        return recipientDb.blockedContacts
    }

    override fun getExpirationConfiguration(threadId: Long): ExpirationConfiguration? {
        val recipient = getRecipientForThread(threadId) ?: return null
        val dbExpirationMetadata = expirationConfigurationDatabase.getExpirationConfiguration(threadId)
        return when {
            recipient.isLocalNumber -> configFactory.withUserConfigs { it.userProfile.getNtsExpiry() }
            recipient.isContactRecipient -> {
                // read it from contacts config if exists
                recipient.address.serialize().takeIf { it.startsWith(IdPrefix.STANDARD.value) }
                    ?.let { configFactory.withUserConfigs { configs -> configs.contacts.get(it)?.expiryMode } }
            }
            recipient.isClosedGroupV2Recipient -> {
                configFactory.withGroupConfigs(AccountId(recipient.address.serialize())) { configs ->
                    configs.groupInfo.getExpiryTimer()
                }.let {
                    if (it == 0L) ExpiryMode.NONE else ExpiryMode.AfterSend(it)
                }
            }
            recipient.isLegacyClosedGroupRecipient -> {
                // read it from group config if exists
                GroupUtil.doubleDecodeGroupId(recipient.address.serialize())
                    .let { id -> configFactory.withUserConfigs { it.userGroups.getLegacyGroupInfo(id) } }
                    ?.run { disappearingTimer.takeIf { it != 0L }?.let(ExpiryMode::AfterSend) ?: ExpiryMode.NONE }
            }
            else -> null
        }?.let { ExpirationConfiguration(
            threadId,
            it,
            // This will be 0L for new closed groups, apparently we don't need this anymore?
            dbExpirationMetadata?.updatedTimestampMs ?: 0L
        ) }
    }

    override fun setExpirationConfiguration(config: ExpirationConfiguration) {
        val recipient = getRecipientForThread(config.threadId) ?: return

        val expirationDb = expirationConfigurationDatabase
        val currentConfig = expirationDb.getExpirationConfiguration(config.threadId)
        if (currentConfig != null && currentConfig.updatedTimestampMs >= config.updatedTimestampMs) return
        val expiryMode = config.expiryMode

        if (expiryMode == ExpiryMode.NONE) {
            // Clear the legacy recipients on updating config to be none
            lokiAPIDatabase.setLastLegacySenderAddress(recipient.address.serialize(), null)
        }

        if (recipient.isLegacyClosedGroupRecipient) {
            val groupPublicKey = GroupUtil.addressToGroupAccountId(recipient.address)

            configFactory.withMutableUserConfigs {
                val groupInfo = it.userGroups.getLegacyGroupInfo(groupPublicKey)
                    ?.copy(disappearingTimer = expiryMode.expirySeconds) ?: return@withMutableUserConfigs
                it.userGroups.set(groupInfo)
            }
        } else if (recipient.isClosedGroupV2Recipient) {
            val groupSessionId = AccountId(recipient.address.serialize())
            configFactory.withMutableGroupConfigs(groupSessionId) { configs ->
                configs.groupInfo.setExpiryTimer(expiryMode.expirySeconds)
            }

        } else if (recipient.isLocalNumber) {
            configFactory.withMutableUserConfigs {
                it.userProfile.setNtsExpiry(expiryMode)
            }
        } else if (recipient.isContactRecipient) {
            configFactory.withMutableUserConfigs {
                val contact = it.contacts.get(recipient.address.serialize())?.copy(expiryMode = expiryMode) ?: return@withMutableUserConfigs
                it.contacts.set(contact)
            }
        }
        expirationDb.setExpirationConfiguration(
            config.run { copy(expiryMode = expiryMode) }
        )
    }

    override fun getExpiringMessages(messageIds: List<Long>): List<Pair<Long, Long>> {
        val expiringMessages = mutableListOf<Pair<Long, Long>>()
        val smsDb = smsDatabase
        smsDb.readerFor(smsDb.expirationNotStartedMessages).use { reader ->
            while (reader.next != null) {
                if (messageIds.isEmpty() || reader.current.id in messageIds) {
                    expiringMessages.add(reader.current.id to reader.current.expiresIn)
                }
            }
        }
        val mmsDb = mmsDatabase
        mmsDb.expireNotStartedMessages.use { reader ->
            while (reader.next != null) {
                if (messageIds.isEmpty() || reader.current.id in messageIds) {
                    expiringMessages.add(reader.current.id to reader.current.expiresIn)
                }
            }
        }
        return expiringMessages
    }

    override fun updateDisappearingState(
        messageSender: String,
        threadID: Long,
        disappearingState: Recipient.DisappearingState
    ) {
        val threadDb = threadDatabase
        val lokiDb = lokiAPIDatabase
        val recipient = threadDb.getRecipientForThreadId(threadID) ?: return
        val recipientAddress = recipient.address.serialize()
        recipientDatabase
            .setDisappearingState(recipient, disappearingState);
        val currentLegacyRecipient = lokiDb.getLastLegacySenderAddress(recipientAddress)
        val currentExpiry = getExpirationConfiguration(threadID)
        if (disappearingState == DisappearingState.LEGACY
            && currentExpiry?.isEnabled == true
            && ExpirationConfiguration.isNewConfigEnabled) { // only set "this person is legacy" if new config enabled
            lokiDb.setLastLegacySenderAddress(recipientAddress, messageSender)
        } else if (messageSender == currentLegacyRecipient) {
            lokiDb.setLastLegacySenderAddress(recipientAddress, null)
        }
    }
}
