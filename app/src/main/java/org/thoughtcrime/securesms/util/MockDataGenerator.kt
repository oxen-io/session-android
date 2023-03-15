package org.thoughtcrime.securesms.util

import android.content.Context
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.messages.signal.IncomingTextMessage
import org.session.libsession.messaging.messages.signal.OutgoingTextMessage
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.crypto.ecc.Curve
import org.session.libsignal.messages.SignalServiceGroup
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.guava.Optional
import org.session.libsignal.utilities.hexEncodedPublicKey
import org.thoughtcrime.securesms.crypto.KeyPairUtilities
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.groups.GroupManager
import java.security.SecureRandom
import kotlin.random.asKotlinRandom

object MockDataGenerator {
    private var printProgress = true
    private var hasStartedGenerationThisRun = false

    // FIXME: Update this to run in a transaction instead of individual db writes (should drastically speed it up)
    fun generateMockData(context: Context) {
        // Don't re-generate the mock data if it already exists
        val mockDataExistsRecipient = Recipient.from(context, Address.fromSerialized("MockDatabaseThread"), false)
        val storage = MessagingModuleConfiguration.shared.storage
        val threadDb = DatabaseComponent.get(context).threadDatabase()
        val lokiThreadDB = DatabaseComponent.get(context).lokiThreadDatabase()
        val contactDb = DatabaseComponent.get(context).sessionContactDatabase()
        val recipientDb = DatabaseComponent.get(context).recipientDatabase()
        val smsDb = DatabaseComponent.get(context).smsDatabase()

        if (hasStartedGenerationThisRun || threadDb.getThreadIdIfExistsFor(mockDataExistsRecipient) != -1L) {
            hasStartedGenerationThisRun = true
            return
        }

        /// The mock data generation is quite slow, there are 3 parts which take a decent amount of time (deleting the account afterwards will
        /// also take a long time):
        ///     Generating the threads & content - ~3m per 100
        val dmThreadCount: Int = 1000
        val closedGroupThreadCount: Int = 50
        val openGroupThreadCount: Int = 20
        val messageRangePerThread: List<IntRange> = listOf(0..500)
        val dmRandomSeed: String = "1111"
        val cgRandomSeed: String = "2222"
        val ogRandomSeed: String = "3333"
        val chunkSize: Int = 1000    // Chunk up the thread writing to prevent memory issues
        val stringContent: List<String> = "abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789 ".map { it.toString() }
        val wordContent: List<String> = listOf("alias", "consequatur", "aut", "perferendis", "sit", "voluptatem", "accusantium", "doloremque", "aperiam", "eaque", "ipsa", "quae", "ab", "illo", "inventore", "veritatis", "et", "quasi", "architecto", "beatae", "vitae", "dicta", "sunt", "explicabo", "aspernatur", "aut", "odit", "aut", "fugit", "sed", "quia", "consequuntur", "magni", "dolores", "eos", "qui", "ratione", "voluptatem", "sequi", "nesciunt", "neque", "dolorem", "ipsum", "quia", "dolor", "sit", "amet", "consectetur", "adipisci", "velit", "sed", "quia", "non", "numquam", "eius", "modi", "tempora", "incidunt", "ut", "labore", "et", "dolore", "magnam", "aliquam", "quaerat", "voluptatem", "ut", "enim", "ad", "minima", "veniam", "quis", "nostrum", "exercitationem", "ullam", "corporis", "nemo", "enim", "ipsam", "voluptatem", "quia", "voluptas", "sit", "suscipit", "laboriosam", "nisi", "ut", "aliquid", "ex", "ea", "commodi", "consequatur", "quis", "autem", "vel", "eum", "iure", "reprehenderit", "qui", "in", "ea", "voluptate", "velit", "esse", "quam", "nihil", "molestiae", "et", "iusto", "odio", "dignissimos", "ducimus", "qui", "blanditiis", "praesentium", "laudantium", "totam", "rem", "voluptatum", "deleniti", "atque", "corrupti", "quos", "dolores", "et", "quas", "molestias", "excepturi", "sint", "occaecati", "cupiditate", "non", "provident", "sed", "ut", "perspiciatis", "unde", "omnis", "iste", "natus", "error", "similique", "sunt", "in", "culpa", "qui", "officia", "deserunt", "mollitia", "animi", "id", "est", "laborum", "et", "dolorum", "fuga", "et", "harum", "quidem", "rerum", "facilis", "est", "et", "expedita", "distinctio", "nam", "libero", "tempore", "cum", "soluta", "nobis", "est", "eligendi", "optio", "cumque", "nihil", "impedit", "quo", "porro", "quisquam", "est", "qui", "minus", "id", "quod", "maxime", "placeat", "facere", "possimus", "omnis", "voluptas", "assumenda", "est", "omnis", "dolor", "repellendus", "temporibus", "autem", "quibusdam", "et", "aut", "consequatur", "vel", "illum", "qui", "dolorem", "eum", "fugiat", "quo", "voluptas", "nulla", "pariatur", "at", "vero", "eos", "et", "accusamus", "officiis", "debitis", "aut", "rerum", "necessitatibus", "saepe", "eveniet", "ut", "et", "voluptates", "repudiandae", "sint", "et", "molestiae", "non", "recusandae", "itaque", "earum", "rerum", "hic", "tenetur", "a", "sapiente", "delectus", "ut", "aut", "reiciendis", "voluptatibus", "maiores", "doloribus", "asperiores", "repellat")
        val timestampNow: Long = System.currentTimeMillis()
        val userSessionId: String = MessagingModuleConfiguration.shared.storage.getUserPublicKey()!!
        val logProgress: ((String, String) -> Unit) = logProgress@{ title, event ->
            if (!printProgress) { return@logProgress }

            Log.i("[MockDataGenerator]", "${System.currentTimeMillis()} $title - $event")
        }

        hasStartedGenerationThisRun = true

        // FIXME: Make sure this data doesn't go off device somehow?
        logProgress("", "Start")

        // First create the thread used to indicate that the mock data has been generated
        threadDb.getOrCreateThreadIdFor(mockDataExistsRecipient)

        // -- DM Thread
        val dmThreadRandomGenerator: SecureRandom = SecureRandom(dmRandomSeed.toByteArray())
        var dmThreadIndex: Int = 0
        logProgress("DM Threads", "Start Generating $dmThreadCount threads")

        while (dmThreadIndex < dmThreadCount) {
            val remainingThreads: Int = (dmThreadCount - dmThreadIndex)

            (0 until Math.min(chunkSize, remainingThreads)).forEach { index ->
                val threadIndex: Int = (dmThreadIndex + index)

                logProgress("DM Thread $threadIndex", "Start")

                val dataBytes = (0 until 16).map { dmThreadRandomGenerator.nextInt(UByte.MAX_VALUE.toInt()).toByte() }
                val randomSessionId: String = KeyPairUtilities.generate(dataBytes.toByteArray()).x25519KeyPair.hexEncodedPublicKey
                val isMessageRequest: Boolean = dmThreadRandomGenerator.nextBoolean()
                val contactNameLength: Int = (5 + dmThreadRandomGenerator.nextInt(15))

                val numMessages: Int = (
                    messageRangePerThread[threadIndex % messageRangePerThread.count()].first +
                    dmThreadRandomGenerator.nextInt(messageRangePerThread[threadIndex % messageRangePerThread.count()].last())
                )

                // Generate the thread
                val recipient = Recipient.from(context, Address.fromSerialized(randomSessionId), false)
                val contact = Contact(randomSessionId)
                val threadId = threadDb.getOrCreateThreadIdFor(recipient)

                // Generate the contact
                val contactIsApproved: Boolean = (!isMessageRequest || dmThreadRandomGenerator.nextBoolean())
                contactDb.setContact(contact)
                contactDb.setContactIsTrusted(contact, true, threadId)
                recipientDb.setApproved(recipient, contactIsApproved)
                recipientDb.setApprovedMe(recipient, (!isMessageRequest && (dmThreadRandomGenerator.nextInt(10) < 8))) // 80% approved the current user

                contact.name = (0 until dmThreadRandomGenerator.nextInt(contactNameLength))
                        .map { stringContent.random(dmThreadRandomGenerator.asKotlinRandom()) }
                        .joinToString()
                recipientDb.setProfileName(recipient, contact.name)
                contactDb.setContact(contact)

                // Generate the message history (Note: Unapproved message requests will only include incoming messages)
                logProgress("DM Thread $threadIndex", "Generate $numMessages Messages")
                (0 until numMessages).forEach { index ->
                    val isIncoming: Boolean = (
                        dmThreadRandomGenerator.nextBoolean() &&
                        (!isMessageRequest || contactIsApproved)
                    )
                    val messageWords: Int = (1 + dmThreadRandomGenerator.nextInt(19))

                    if (isIncoming) {
                        smsDb.insertMessageInbox(
                            IncomingTextMessage(
                                recipient.address,
                                1,
                                (timestampNow - (index * 5000)),
                                (0 until messageWords)
                                        .map { wordContent.random(dmThreadRandomGenerator.asKotlinRandom()) }
                                        .joinToString(),
                                Optional.absent(),
                                0,
                                false,
                                -1,
                                false
                            ),
                            (timestampNow - (index * 5000)),
                            false
                        )
                    }
                    else {
                        smsDb.insertMessageOutbox(
                            threadId,
                            OutgoingTextMessage(
                                recipient,
                                (0 until messageWords)
                                    .map { wordContent.random(dmThreadRandomGenerator.asKotlinRandom()) }
                                    .joinToString(),
                                0,
                                -1,
                                (timestampNow - (index * 5000))
                            ),
                            (timestampNow - (index * 5000)),
                            false
                        )
                    }
                }

                logProgress("DM Thread $threadIndex", "Done")
            }
            logProgress("DM Threads", "Done")

            dmThreadIndex += chunkSize
        }
        logProgress("DM Threads", "Done")

        // -- Closed Group

        val cgThreadRandomGenerator: SecureRandom = SecureRandom(cgRandomSeed.toByteArray())
        var cgThreadIndex: Int = 0
        logProgress("Closed Group Threads", "Start Generating $closedGroupThreadCount threads")

        while (cgThreadIndex < closedGroupThreadCount) {
            val remainingThreads: Int = (closedGroupThreadCount - cgThreadIndex)

            (0 until Math.min(chunkSize, remainingThreads)).forEach { index ->
                val threadIndex: Int = (cgThreadIndex + index)

                logProgress("Closed Group Thread $threadIndex", "Start")

                val dataBytes = (0 until 16).map { cgThreadRandomGenerator.nextInt(UByte.MAX_VALUE.toInt()).toByte() }
                val randomGroupPublicKey: String = KeyPairUtilities.generate(dataBytes.toByteArray()).x25519KeyPair.hexEncodedPublicKey
                val groupNameLength: Int = (5 + cgThreadRandomGenerator.nextInt(15))
                val groupName: String = (0 until groupNameLength)
                    .map { stringContent.random(cgThreadRandomGenerator.asKotlinRandom()) }
                    .joinToString()
                val numGroupMembers: Int = cgThreadRandomGenerator.nextInt (10)
                val numMessages: Int = (
                    messageRangePerThread[threadIndex % messageRangePerThread.count()].first +
                    cgThreadRandomGenerator.nextInt(messageRangePerThread[threadIndex % messageRangePerThread.count()].last())
                )

                // Generate the Contacts in the group
                val members: MutableList<String> = mutableListOf(userSessionId)
                logProgress("Closed Group Thread $threadIndex", "Generate $numGroupMembers Contacts")

                (0 until numGroupMembers).forEach {
                    val contactBytes = (0 until 16).map { cgThreadRandomGenerator.nextInt(UByte.MAX_VALUE.toInt()).toByte() }
                    val randomSessionId: String = KeyPairUtilities.generate(contactBytes.toByteArray()).x25519KeyPair.hexEncodedPublicKey
                    val contactNameLength: Int = (5 + cgThreadRandomGenerator.nextInt(15))

                    val recipient = Recipient.from(context, Address.fromSerialized(randomSessionId), false)
                    val contact = Contact(randomSessionId)
                    contactDb.setContact(contact)
                    recipientDb.setApproved(recipient, true)
                    recipientDb.setApprovedMe(recipient, true)

                    contact.name = (0 until cgThreadRandomGenerator.nextInt(contactNameLength))
                            .map { stringContent.random(cgThreadRandomGenerator.asKotlinRandom()) }
                            .joinToString()
                    recipientDb.setProfileName(recipient, contact.name)
                    contactDb.setContact(contact)
                    members.add(randomSessionId)
                }

                val groupId = GroupUtil.doubleEncodeGroupID(randomGroupPublicKey)
                val threadId = storage.getOrCreateThreadIdFor(Address.fromSerialized(groupId))
                val adminUserId = members.random(cgThreadRandomGenerator.asKotlinRandom())
                storage.createGroup(
                    groupId,
                    groupName,
                    members.map { Address.fromSerialized(it) },
                    null,
                    null,
                    listOf(Address.fromSerialized(adminUserId)),
                    timestampNow
                )
                storage.setProfileSharing(Address.fromSerialized(groupId), true)
                storage.addClosedGroupPublicKey(randomGroupPublicKey)

                // Add the group to the user's set of public keys to poll for and store the key pair
                val encryptionKeyPair = Curve.generateKeyPair()
                storage.addClosedGroupEncryptionKeyPair(encryptionKeyPair, randomGroupPublicKey, System.currentTimeMillis())
                storage.setExpirationTimer(groupId, 0)
                storage.createInitialConfigGroup(randomGroupPublicKey, groupName, GroupUtil.createConfigMemberMap(members, setOf(adminUserId)), System.currentTimeMillis(), encryptionKeyPair)

                // Add the group created message
                if (userSessionId == adminUserId) {
                    storage.insertOutgoingInfoMessage(context, groupId, SignalServiceGroup.Type.CREATION, groupName, members, listOf(adminUserId), threadId, (timestampNow - (numMessages * 5000)))
                }
                else {
                    storage.insertIncomingInfoMessage(context, adminUserId, groupId, SignalServiceGroup.Type.CREATION, groupName, members, listOf(adminUserId), (timestampNow - (numMessages * 5000)))
                }

                // Generate the message history (Note: Unapproved message requests will only include incoming messages)
                logProgress("Closed Group Thread $threadIndex", "Generate $numMessages Messages")

                (0 until numGroupMembers).forEach {
                    val messageWords: Int = (1 + cgThreadRandomGenerator.nextInt(19))
                    val senderId: String = members.random(cgThreadRandomGenerator.asKotlinRandom())

                    if (senderId != userSessionId) {
                        smsDb.insertMessageInbox(
                            IncomingTextMessage(
                                Address.fromSerialized(senderId),
                                1,
                                (timestampNow - (index * 5000)),
                                (0 until messageWords)
                                    .map { wordContent.random(cgThreadRandomGenerator.asKotlinRandom()) }
                                    .joinToString(),
                                Optional.absent(),
                                0,
                                false,
                                -1,
                                false
                            ),
                            (timestampNow - (index * 5000)),
                            false
                        )
                    }
                    else {
                        smsDb.insertMessageOutbox(
                            threadId,
                            OutgoingTextMessage(
                                threadDb.getRecipientForThreadId(threadId),
                                (0 until messageWords)
                                    .map { wordContent.random(cgThreadRandomGenerator.asKotlinRandom()) }
                                    .joinToString(),
                                0,
                                -1,
                                (timestampNow - (index * 5000))
                            ),
                            (timestampNow - (index * 5000)),
                            false
                        )
                    }
                }

                logProgress("Closed Group Thread $threadIndex", "Done")
            }

            cgThreadIndex += chunkSize
        }
        logProgress("Closed Group Threads", "Done")

        // --Open Group

        val ogThreadRandomGenerator: SecureRandom = SecureRandom(cgRandomSeed.toByteArray())
        var ogThreadIndex: Int = 0
        logProgress("Open Group Threads", "Start Generating $openGroupThreadCount threads")

        while (ogThreadIndex < openGroupThreadCount) {
            val remainingThreads: Int = (openGroupThreadCount - ogThreadIndex)

            (0 until Math.min(chunkSize, remainingThreads)).forEach { index ->
                val threadIndex: Int = (ogThreadIndex + index)

                logProgress("Open Group Thread $threadIndex", "Start")

                val dataBytes = (0 until 32).map { ogThreadRandomGenerator.nextInt(UByte.MAX_VALUE.toInt()).toByte() }
                val randomGroupPublicKey: String = KeyPairUtilities.generate(dataBytes.toByteArray()).x25519KeyPair.hexEncodedPublicKey
                val serverNameLength: Int = (5 + ogThreadRandomGenerator.nextInt(15))
                val roomNameLength: Int = (5 + ogThreadRandomGenerator.nextInt(15))
                val roomDescriptionLength: Int = (10 + ogThreadRandomGenerator.nextInt(40))
                val serverName: String = (0 until serverNameLength)
                    .map { stringContent.random(ogThreadRandomGenerator.asKotlinRandom()) }
                    .joinToString()
                val roomName: String = (0 until roomNameLength)
                    .map { stringContent.random(ogThreadRandomGenerator.asKotlinRandom()) }
                    .joinToString()
                val roomDescription: String = (0 until roomDescriptionLength)
                    .map { stringContent.random(ogThreadRandomGenerator.asKotlinRandom()) }
                    .joinToString()
                val numGroupMembers: Int = ogThreadRandomGenerator.nextInt(250)
                val numMessages: Int = (
                    messageRangePerThread[threadIndex % messageRangePerThread.count()].first +
                    ogThreadRandomGenerator.nextInt(messageRangePerThread[threadIndex % messageRangePerThread.count()].last())
                )

                // Generate the Contacts in the group
                val members: MutableList<String> = mutableListOf(userSessionId)
                logProgress("Open Group Thread $threadIndex", "Generate $numGroupMembers Contacts")

                (0 until numGroupMembers).forEach {
                    val contactBytes = (0 until 16).map { ogThreadRandomGenerator.nextInt(UByte.MAX_VALUE.toInt()).toByte() }
                    val randomSessionId: String = KeyPairUtilities.generate(contactBytes.toByteArray()).x25519KeyPair.hexEncodedPublicKey
                    val contactNameLength: Int = (5 + ogThreadRandomGenerator.nextInt(15))

                    val recipient = Recipient.from(context, Address.fromSerialized(randomSessionId), false)
                    val contact = Contact(randomSessionId)
                    contactDb.setContact(contact)
                    recipientDb.setApproved(recipient, true)
                    recipientDb.setApprovedMe(recipient, true)

                    contact.name = (0 until ogThreadRandomGenerator.nextInt(contactNameLength))
                            .map { stringContent.random(cgThreadRandomGenerator.asKotlinRandom()) }
                            .joinToString()
                    recipientDb.setProfileName(recipient, contact.name)
                    contactDb.setContact(contact)
                    members.add(randomSessionId)
                }

                // Create the open group model and the thread
                val openGroupId = "$serverName.$roomName"
                val threadId = GroupManager.createOpenGroup(openGroupId, context, null, roomName).threadId
                val hasBlinding: Boolean = ogThreadRandomGenerator.nextBoolean()

                // Generate the capabilities and other data
                storage.setOpenGroupPublicKey(serverName, randomGroupPublicKey)
                storage.setServerCapabilities(
                    serverName,
                    (
                        listOf(OpenGroupApi.Capability.SOGS.name.lowercase()) +
                        if (hasBlinding) { listOf(OpenGroupApi.Capability.BLIND.name.lowercase()) } else { emptyList() }
                    )
                )
                storage.setUserCount(roomName, serverName, numGroupMembers)
                lokiThreadDB.setOpenGroupChat(OpenGroup(server = serverName, room = roomName, publicKey = randomGroupPublicKey, name = roomName, imageId = null, canWrite = true, infoUpdates = 0), threadId)

                // Generate the message history (Note: Unapproved message requests will only include incoming messages)
                logProgress("Open Group Thread $threadIndex", "Generate $numMessages Messages")

                (0 until numMessages).forEach { index ->
                    val messageWords: Int = (1 + ogThreadRandomGenerator.nextInt(19))
                    val senderId: String = members.random(ogThreadRandomGenerator.asKotlinRandom())

                    if (senderId != userSessionId) {
                        smsDb.insertMessageInbox(
                            IncomingTextMessage(
                                Address.fromSerialized(senderId),
                                1,
                                (timestampNow - (index * 5000)),
                                (0 until messageWords)
                                    .map { wordContent.random(ogThreadRandomGenerator.asKotlinRandom()) }
                                    .joinToString(),
                                Optional.absent(),
                                0,
                                false,
                                -1,
                                false
                            ),
                            (timestampNow - (index * 5000)),
                            false
                        )
                    } else {
                        smsDb.insertMessageOutbox(
                            threadId,
                            OutgoingTextMessage(
                                threadDb.getRecipientForThreadId(threadId),
                                (0 until messageWords)
                                    .map { wordContent.random(ogThreadRandomGenerator.asKotlinRandom()) }
                                    .joinToString(),
                                0,
                                -1,
                                (timestampNow - (index * 5000))
                            ),
                            (timestampNow - (index * 5000)),
                            false
                        )
                    }
                }

                logProgress("Open Group Thread $threadIndex", "Done")
            }

            ogThreadIndex += chunkSize
        }

        logProgress("Open Group Threads", "Done")
        logProgress("", "Complete")
    }
}