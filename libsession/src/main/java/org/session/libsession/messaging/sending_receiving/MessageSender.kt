package org.session.libsession.messaging.sending_receiving

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import network.loki.messenger.libsession_util.util.ExpiryMode
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageSendJob
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.applyExpiryMode
import org.session.libsession.messaging.messages.control.CallMessage
import org.session.libsession.messaging.messages.control.ClosedGroupControlMessage
import org.session.libsession.messaging.messages.control.ConfigurationMessage
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.messages.control.GroupUpdated
import org.session.libsession.messaging.messages.control.MessageRequestResponse
import org.session.libsession.messaging.messages.control.SharedConfigurationMessage
import org.session.libsession.messaging.messages.control.UnsendRequest
import org.session.libsession.messaging.messages.visible.LinkPreview
import org.session.libsession.messaging.messages.visible.Quote
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.open_groups.OpenGroupApi.Capability
import org.session.libsession.messaging.open_groups.OpenGroupMessage
import org.session.libsession.messaging.utilities.MessageWrapper
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.snode.RawResponsePromise
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeAPI.nowWithOffset
import org.session.libsession.snode.SnodeMessage
import org.session.libsession.snode.SnodeModule
import org.session.libsession.snode.utilities.asyncPromise
import org.session.libsession.snode.utilities.await
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.Device
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsignal.crypto.PushTransportDetails
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Namespace
import org.session.libsignal.utilities.defaultRequiresAuth
import org.session.libsignal.utilities.hasNamespaces
import org.session.libsignal.utilities.hexEncodedPublicKey
import java.util.concurrent.TimeUnit
import org.session.libsession.messaging.sending_receiving.attachments.Attachment as SignalAttachment
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview as SignalLinkPreview
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel as SignalQuote

object MessageSender {

    // Error
    sealed class Error(val description: String) : Exception(description) {
        object InvalidMessage : Error("Invalid message.")
        object ProtoConversionFailed : Error("Couldn't convert message to proto.")
        object NoUserED25519KeyPair : Error("Couldn't find user ED25519 key pair.")
        object SigningFailed : Error("Couldn't sign message.")
        object EncryptionFailed : Error("Couldn't encrypt message.")
        data class InvalidDestination(val destination: Destination): Error("Can't send this way to $destination")

        // Closed groups
        object NoThread : Error("Couldn't find a thread associated with the given group public key.")
        object NoKeyPair: Error("Couldn't find a private key associated with the given group public key.")
        object InvalidClosedGroupUpdate : Error("Invalid group update.")

        internal val isRetryable: Boolean = when (this) {
            is InvalidMessage, ProtoConversionFailed, InvalidClosedGroupUpdate -> false
            else -> true
        }
    }

    // Convenience
    fun send(message: Message, destination: Destination, isSyncMessage: Boolean): Promise<Unit, Exception> {
        if (message is VisibleMessage) MessagingModuleConfiguration.shared.lastSentTimestampCache.submitTimestamp(message.threadID!!, message.sentTimestamp!!)
        return if (destination is Destination.LegacyOpenGroup || destination is Destination.OpenGroup || destination is Destination.OpenGroupInbox) {
            sendToOpenGroupDestination(destination, message)
        } else {
            GlobalScope.asyncPromise {
                sendToSnodeDestination(destination, message, isSyncMessage)
            }
        }
    }

    fun buildConfigMessageToSnode(destinationPubKey: String, message: SharedConfigurationMessage): SnodeMessage {
        return SnodeMessage(
            destinationPubKey,
            Base64.encodeBytes(message.data),
            ttl = message.ttl,
            SnodeAPI.nowWithOffset
        )
    }

    // One-on-One Chats & Closed Groups
    @Throws(Exception::class)
    fun buildWrappedMessageToSnode(destination: Destination, message: Message, isSyncMessage: Boolean): SnodeMessage {
        val storage = MessagingModuleConfiguration.shared.storage
        val configFactory = MessagingModuleConfiguration.shared.configFactory
        val userPublicKey = storage.getUserPublicKey()
        // Set the timestamp, sender and recipient
        val messageSendTime = nowWithOffset
        if (message.sentTimestamp == null) {
            message.sentTimestamp =
                messageSendTime // Visible messages will already have their sent timestamp set
        }

        message.sender = userPublicKey
        // SHARED CONFIG
        when (destination) {
            is Destination.Contact -> message.recipient = destination.publicKey
            is Destination.LegacyClosedGroup -> message.recipient = destination.groupPublicKey
            is Destination.ClosedGroup -> message.recipient = destination.publicKey
            else -> throw IllegalStateException("Destination should not be an open group.")
        }

        val isSelfSend = (message.recipient == userPublicKey)
        // Validate the message
        if (!message.isValid()) {
            throw Error.InvalidMessage
        }
        // Stop here if this is a self-send, unless it's:
        // • a configuration message
        // • a sync message
        // • a closed group control message of type `new`
        var isNewClosedGroupControlMessage = false
        if (message is ClosedGroupControlMessage && message.kind is ClosedGroupControlMessage.Kind.New) isNewClosedGroupControlMessage =
            true
        if (isSelfSend
            && message !is ConfigurationMessage
            && !isSyncMessage
            && !isNewClosedGroupControlMessage
            && message !is UnsendRequest
            && message !is SharedConfigurationMessage
        ) {
            throw Error.InvalidMessage
        }
        // Attach the user's profile if needed
        if (message is VisibleMessage) {
            message.profile = storage.getUserProfile()
        }
        if (message is MessageRequestResponse) {
            message.profile = storage.getUserProfile()
        }
        // Convert it to protobuf
        val proto = message.toProto()?.toBuilder() ?: throw Error.ProtoConversionFailed
        if (message is GroupUpdated) {
            // Add all cases where we have to attach profile
            if (message.inner.hasInviteResponse()) {
                proto.mergeDataMessage(storage.getUserProfile().toProto())
            }
        }
        // Serialize the protobuf
        val plaintext = PushTransportDetails.getPaddedMessageBody(proto.build().toByteArray())

        // Envelope information
        val kind: SignalServiceProtos.Envelope.Type
        val senderPublicKey: String
        when (destination) {
            is Destination.Contact -> {
                kind = SignalServiceProtos.Envelope.Type.SESSION_MESSAGE
                senderPublicKey = ""
            }
            is Destination.LegacyClosedGroup -> {
                kind = SignalServiceProtos.Envelope.Type.CLOSED_GROUP_MESSAGE
                senderPublicKey = destination.groupPublicKey
            }
            is Destination.ClosedGroup -> {
                kind = SignalServiceProtos.Envelope.Type.CLOSED_GROUP_MESSAGE
                senderPublicKey = destination.publicKey
            }
            else -> throw IllegalStateException("Destination should not be open group.")
        }

        // Encrypt the serialized protobuf
        val ciphertext = when (destination) {
            is Destination.Contact -> MessageEncrypter.encrypt(plaintext, destination.publicKey)
            is Destination.LegacyClosedGroup -> {
                val encryptionKeyPair =
                    MessagingModuleConfiguration.shared.storage.getLatestClosedGroupEncryptionKeyPair(
                        destination.groupPublicKey
                    )!!
                MessageEncrypter.encrypt(plaintext, encryptionKeyPair.hexEncodedPublicKey)
            }
            is Destination.ClosedGroup -> {
                val envelope = MessageWrapper.createEnvelope(kind, message.sentTimestamp!!, senderPublicKey, proto.build().toByteArray())
                configFactory.withGroupConfigs(AccountId(destination.publicKey)) {
                    it.groupKeys.encrypt(envelope.toByteArray())
                }
            }
            else -> throw IllegalStateException("Destination should not be open group.")
        }
        // Wrap the result using envelope information
        val wrappedMessage = when (destination) {
            is Destination.ClosedGroup -> {
                // encrypted bytes from the above closed group encryption and envelope steps
                ciphertext
            }
            else -> MessageWrapper.wrap(kind, message.sentTimestamp!!, senderPublicKey, ciphertext)
        }
        val base64EncodedData = Base64.encodeBytes(wrappedMessage)
        // Send the result
        return SnodeMessage(
            message.recipient!!,
            base64EncodedData,
            ttl = getSpecifiedTtl(message, isSyncMessage) ?: message.ttl,
            messageSendTime
        )
    }

    // One-on-One Chats & Closed Groups
    private suspend fun sendToSnodeDestination(destination: Destination, message: Message, isSyncMessage: Boolean = false) = supervisorScope {
        val storage = MessagingModuleConfiguration.shared.storage
        val configFactory = MessagingModuleConfiguration.shared.configFactory
        val userPublicKey = storage.getUserPublicKey()

        // recipient will be set later, so initialize it as a function here
        val isSelfSend = { message.recipient == userPublicKey }

        // Set the failure handler (need it here already for precondition failure handling)
        fun handleFailure(error: Exception) {
            handleFailedMessageSend(message, error, isSyncMessage)
            if (destination is Destination.Contact && message is VisibleMessage && !isSelfSend()) {
                SnodeModule.shared.broadcaster.broadcast("messageFailed", message.sentTimestamp!!)
            }
        }

        try {
            val snodeMessage = buildWrappedMessageToSnode(destination, message, isSyncMessage)
            // TODO: this might change in future for config messages
            val forkInfo = SnodeAPI.forkInfo
            val namespaces: List<Int> = when {
                destination is Destination.LegacyClosedGroup
                        && forkInfo.defaultRequiresAuth() -> listOf(Namespace.UNAUTHENTICATED_CLOSED_GROUP())

                destination is Destination.LegacyClosedGroup
                        && forkInfo.hasNamespaces() -> listOf(
                    Namespace.UNAUTHENTICATED_CLOSED_GROUP(),
                    Namespace.DEFAULT
                ())
                destination is Destination.ClosedGroup -> listOf(Namespace.CLOSED_GROUP_MESSAGES())

                else -> listOf(Namespace.DEFAULT())
            }

            val sendTasks = namespaces.map { namespace ->
                if (destination is Destination.ClosedGroup) {
                    val groupAuth = requireNotNull(configFactory.getGroupAuth(AccountId(destination.publicKey))) {
                        "Unable to authorize group message send"
                    }

                    async {
                        SnodeAPI.sendMessage(
                            auth = groupAuth,
                            message = snodeMessage,
                            namespace = namespace
                        )
                    }
                } else {
                    async {
                        SnodeAPI.sendMessage(snodeMessage, auth = null, namespace = namespace)
                    }
                }
            }

            val sendTaskResults = sendTasks.map {
                runCatching { it.await() }
            }

            val firstSuccess = sendTaskResults.firstOrNull { it.isSuccess }?.getOrNull()

            if (firstSuccess != null) {
                message.serverHash = firstSuccess.hash
                handleSuccessfulMessageSend(message, destination, isSyncMessage)
            } else {
                // If all tasks failed, throw the first exception
                throw sendTaskResults.first().exceptionOrNull()!!
            }
        } catch (exception: Exception) {
            handleFailure(exception)
            throw exception
        }
    }

    private fun getSpecifiedTtl(
        message: Message,
        isSyncMessage: Boolean
    ): Long? {
        // For ClosedGroupControlMessage or GroupUpdateMemberLeftMessage, the expiration timer doesn't apply
        if (message is ClosedGroupControlMessage || (
                    message is GroupUpdated && (
                            message.inner.hasMemberLeftMessage() ||
                            message.inner.hasInviteMessage() ||
                            message.inner.hasInviteResponse() ||
                            message.inner.hasDeleteMemberContent() ||
                            message.inner.hasPromoteMessage()))) {
            return null
        }

        // Otherwise the expiration configuration applies
        return message.run {
            threadID ?: (if (isSyncMessage && this is VisibleMessage) syncTarget else recipient)
                ?.let(Address.Companion::fromSerialized)
                ?.let(MessagingModuleConfiguration.shared.storage::getThreadId)
        }
            ?.let(MessagingModuleConfiguration.shared.storage::getExpirationConfiguration)
            ?.takeIf { it.isEnabled }
            ?.expiryMode
            ?.takeIf { it is ExpiryMode.AfterSend || isSyncMessage }
            ?.expiryMillis
    }

    // Open Groups
    private fun sendToOpenGroupDestination(destination: Destination, message: Message): Promise<Unit, Exception> {
        val deferred = deferred<Unit, Exception>()
        val storage = MessagingModuleConfiguration.shared.storage
        val configFactory = MessagingModuleConfiguration.shared.configFactory
        if (message.sentTimestamp == null) {
            message.sentTimestamp = nowWithOffset
        }
        // Attach the blocks message requests info
        configFactory.withUserConfigs { configs ->
            if (message is VisibleMessage) {
                message.blocksMessageRequests = !configs.userProfile.getCommunityMessageRequests()
            }
        }
        val userEdKeyPair = MessagingModuleConfiguration.shared.storage.getUserED25519KeyPair()!!
        var serverCapabilities = listOf<String>()
        var blindedPublicKey: ByteArray? = null
        when(destination) {
            is Destination.OpenGroup -> {
                serverCapabilities = storage.getServerCapabilities(destination.server)
                storage.getOpenGroup(destination.roomToken, destination.server)?.let {
                    blindedPublicKey = SodiumUtilities.blindedKeyPair(it.publicKey, userEdKeyPair)?.publicKey?.asBytes
                }
            }
            is Destination.OpenGroupInbox -> {
                serverCapabilities = storage.getServerCapabilities(destination.server)
                blindedPublicKey = SodiumUtilities.blindedKeyPair(destination.serverPublicKey, userEdKeyPair)?.publicKey?.asBytes
            }
            is Destination.LegacyOpenGroup -> {
                serverCapabilities = storage.getServerCapabilities(destination.server)
                storage.getOpenGroup(destination.roomToken, destination.server)?.let {
                    blindedPublicKey = SodiumUtilities.blindedKeyPair(it.publicKey, userEdKeyPair)?.publicKey?.asBytes
                }
            }
            else -> {}
        }
        val messageSender = if (serverCapabilities.contains(Capability.BLIND.name.lowercase()) && blindedPublicKey != null) {
            AccountId(IdPrefix.BLINDED, blindedPublicKey!!).hexString
        } else {
            AccountId(IdPrefix.UN_BLINDED, userEdKeyPair.publicKey.asBytes).hexString
        }
        message.sender = messageSender
        // Set the failure handler (need it here already for precondition failure handling)
        fun handleFailure(error: Exception) {
            handleFailedMessageSend(message, error)
            deferred.reject(error)
        }
        try {
            // Attach the user's profile if needed
            if (message is VisibleMessage) {
                message.profile = storage.getUserProfile()
            }
            when (destination) {
                is Destination.OpenGroup -> {
                    val whisperMods = if (destination.whisperTo.isNullOrEmpty() && destination.whisperMods) "mods" else null
                    message.recipient = "${destination.server}.${destination.roomToken}.${destination.whisperTo}.$whisperMods"
                    // Validate the message
                    if (message !is VisibleMessage || !message.isValid()) {
                        throw Error.InvalidMessage
                    }
                    val messageBody = message.toProto()?.toByteArray()!!
                    val plaintext = PushTransportDetails.getPaddedMessageBody(messageBody)
                    val openGroupMessage = OpenGroupMessage(
                        sender = message.sender,
                        sentTimestamp = message.sentTimestamp!!,
                        base64EncodedData = Base64.encodeBytes(plaintext),
                    )
                    OpenGroupApi.sendMessage(openGroupMessage, destination.roomToken, destination.server, destination.whisperTo, destination.whisperMods, destination.fileIds).success {
                        message.openGroupServerMessageID = it.serverID
                        handleSuccessfulMessageSend(message, destination, openGroupSentTimestamp = it.sentTimestamp)
                        deferred.resolve(Unit)
                    }.fail {
                        handleFailure(it)
                    }
                }
                is Destination.OpenGroupInbox -> {
                    message.recipient = destination.blindedPublicKey
                    // Validate the message
                    if (message !is VisibleMessage || !message.isValid()) {
                        throw Error.InvalidMessage
                    }
                    val messageBody = message.toProto()?.toByteArray()!!
                    val plaintext = PushTransportDetails.getPaddedMessageBody(messageBody)
                    val ciphertext = MessageEncrypter.encryptBlinded(
                        plaintext,
                        destination.blindedPublicKey,
                        destination.serverPublicKey
                    )
                    val base64EncodedData = Base64.encodeBytes(ciphertext)
                    OpenGroupApi.sendDirectMessage(base64EncodedData, destination.blindedPublicKey, destination.server).success {
                        message.openGroupServerMessageID = it.id
                        handleSuccessfulMessageSend(message, destination, openGroupSentTimestamp = TimeUnit.SECONDS.toMillis(it.postedAt))
                        deferred.resolve(Unit)
                    }.fail {
                        handleFailure(it)
                    }
                }
                else -> throw IllegalStateException("Invalid destination.")
            }
        } catch (exception: Exception) {
            handleFailure(exception)
        }
        return deferred.promise
    }

    // Result Handling
    fun handleSuccessfulMessageSend(message: Message, destination: Destination, isSyncMessage: Boolean = false, openGroupSentTimestamp: Long = -1) {
        if (message is VisibleMessage) MessagingModuleConfiguration.shared.lastSentTimestampCache.submitTimestamp(message.threadID!!, openGroupSentTimestamp)
        val storage = MessagingModuleConfiguration.shared.storage
        val userPublicKey = storage.getUserPublicKey()!!
        val timestamp = message.sentTimestamp!!
        // Ignore future self-sends
        storage.addReceivedMessageTimestamp(timestamp)
        storage.getMessageIdInDatabase(timestamp, userPublicKey)?.let { (messageID, mms) ->
            if (openGroupSentTimestamp != -1L && message is VisibleMessage) {
                storage.addReceivedMessageTimestamp(openGroupSentTimestamp)
                storage.updateSentTimestamp(messageID, message.isMediaMessage(), openGroupSentTimestamp, message.threadID!!)
                message.sentTimestamp = openGroupSentTimestamp
            }

            // When the sync message is successfully sent, the hash value of this TSOutgoingMessage
            // will be replaced by the hash value of the sync message. Since the hash value of the
            // real message has no use when we delete a message. It is OK to let it be.
            message.serverHash?.let {
                storage.setMessageServerHash(messageID, mms, it)
            }

            // in case any errors from previous sends
            storage.clearErrorMessage(messageID)

            // Track the open group server message ID
            val messageIsAddressedToCommunity = message.openGroupServerMessageID != null && (destination is Destination.LegacyOpenGroup || destination is Destination.OpenGroup)
            if (messageIsAddressedToCommunity) {
                val server: String
                val room: String
                when (destination) {
                    is Destination.LegacyOpenGroup -> {
                        server = destination.server
                        room = destination.roomToken
                    }
                    is Destination.OpenGroup -> {
                        server = destination.server
                        room = destination.roomToken
                    }
                    else -> {
                        throw Exception("Destination was a different destination than we were expecting")
                    }
                }
                val encoded = GroupUtil.getEncodedOpenGroupID("$server.$room".toByteArray())
                val threadID = storage.getThreadId(Address.fromSerialized(encoded))
                if (threadID != null && threadID >= 0) {
                    storage.setOpenGroupServerMessageID(messageID, message.openGroupServerMessageID!!, threadID, !(message as VisibleMessage).isMediaMessage())
                }
            }

            // Mark the message as sent.
            // Note: When sending a message to a community the server modifies the message timestamp
            // so when we go to look up the message in the local database by timestamp it fails and
            // we're left with the message delivery status as "Sending" forever! As such, we use a
            // pair of modified "markAsSentToCommunity" and "markUnidentifiedInCommunity" methods
            // to retrieve the local message by thread & message ID rather than timestamp when
            // handling community messages only so we can tick the delivery status over to 'Sent'.
            // Fixed in: https://optf.atlassian.net/browse/SES-1567
            if (messageIsAddressedToCommunity)
            {
                storage.markAsSentToCommunity(message.threadID!!, message.id!!)
                storage.markUnidentifiedInCommunity(message.threadID!!, message.id!!)
            }
            else
            {
                storage.markAsSent(timestamp, userPublicKey)
                storage.markUnidentified(timestamp, userPublicKey)
            }

            // Start the disappearing messages timer if needed
            SSKEnvironment.shared.messageExpirationManager.maybeStartExpiration(message, startDisappearAfterRead = true)
        } ?: run {
            storage.updateReactionIfNeeded(message, message.sender?:userPublicKey, openGroupSentTimestamp)
        }
        // Sync the message if:
        // • it's a visible message
        // • the destination was a contact
        // • we didn't sync it already
        if (destination is Destination.Contact && !isSyncMessage) {
            if (message is VisibleMessage) message.syncTarget = destination.publicKey
            if (message is ExpirationTimerUpdate) message.syncTarget = destination.publicKey

            storage.markAsSyncing(timestamp, userPublicKey)
            GlobalScope.launch {
                sendToSnodeDestination(Destination.Contact(userPublicKey), message, true)
            }
        }
    }

    fun handleFailedMessageSend(message: Message, error: Exception, isSyncMessage: Boolean = false) {
        val storage = MessagingModuleConfiguration.shared.storage
        val userPublicKey = storage.getUserPublicKey()!!

        val timestamp = message.sentTimestamp!!
        val author = message.sender ?: userPublicKey

        if (isSyncMessage) storage.markAsSyncFailed(timestamp, author, error)
        else storage.markAsSentFailed(timestamp, author, error)
    }

    // Convenience
    @JvmStatic
    fun send(message: VisibleMessage, address: Address, attachments: List<SignalAttachment>, quote: SignalQuote?, linkPreview: SignalLinkPreview?) {
        val messageDataProvider = MessagingModuleConfiguration.shared.messageDataProvider
        val attachmentIDs = messageDataProvider.getAttachmentIDsFor(message.id!!)
        message.attachmentIDs.addAll(attachmentIDs)
        message.quote = Quote.from(quote)
        message.linkPreview = LinkPreview.from(linkPreview)
        message.linkPreview?.let { linkPreview ->
            if (linkPreview.attachmentID == null) {
                messageDataProvider.getLinkPreviewAttachmentIDFor(message.id!!)?.let { attachmentID ->
                    linkPreview.attachmentID = attachmentID
                    message.attachmentIDs.remove(attachmentID)
                }
            }
        }
        send(message, address)
    }

    @JvmStatic
    fun send(message: Message, address: Address) {
        val threadID = MessagingModuleConfiguration.shared.storage.getThreadId(address)
        threadID?.let(message::applyExpiryMode)
        message.threadID = threadID
        val destination = Destination.from(address)
        val job = MessageSendJob(message, destination)
        JobQueue.shared.add(job)
    }

    fun sendNonDurably(message: VisibleMessage, attachments: List<SignalAttachment>, address: Address, isSyncMessage: Boolean): Promise<Unit, Exception> {
        val attachmentIDs = MessagingModuleConfiguration.shared.messageDataProvider.getAttachmentIDsFor(message.id!!)
        message.attachmentIDs.addAll(attachmentIDs)
        return sendNonDurably(message, address, isSyncMessage)
    }

    fun sendNonDurably(message: Message, address: Address, isSyncMessage: Boolean): Promise<Unit, Exception> {
        val threadID = MessagingModuleConfiguration.shared.storage.getThreadId(address)
        message.threadID = threadID
        val destination = Destination.from(address)
        return send(message, destination, isSyncMessage)
    }

    // Closed groups
    fun createClosedGroup(device: Device, name: String, members: Collection<String>): Promise<String, Exception> {
        return create(device, name, members)
    }

    fun explicitNameChange(groupPublicKey: String, newName: String) {
        return setName(groupPublicKey, newName)
    }

    fun explicitAddMembers(groupPublicKey: String, membersToAdd: List<String>) {
        return addMembers(groupPublicKey, membersToAdd)
    }

    fun explicitRemoveMembers(groupPublicKey: String, membersToRemove: List<String>) {
        return removeMembers(groupPublicKey, membersToRemove)
    }

    @JvmStatic
    fun explicitLeave(groupPublicKey: String, notifyUser: Boolean, deleteThread: Boolean = false) {
        leave(groupPublicKey, notifyUser, deleteThread)
    }

}