package org.session.libsession.messaging.messages

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.utilities.toHexString

sealed class Destination {

    class Contact(var publicKey: String) : Destination() {
        internal constructor(): this("")
    }
    class ClosedGroup(var groupPublicKey: String) : Destination() {
        internal constructor(): this("")
    }
    class LegacyOpenGroup(var roomToken: String, var server: String) : Destination() {
        internal constructor(): this("", "")
    }

    class OpenGroup(
        val roomToken: String,
        val server: String,
        val whisperTo: List<String>? = null,
        val whisperMods: Boolean = false,
        val fileIds: List<String>? = null
    ) : Destination()

    class OpenGroupInbox(
        val server: String,
        val serverPublicKey: String,
        val blinkedPublicKey: String
    ) : Destination()

    companion object {

        fun from(address: Address, fileIds: List<String>? = null): Destination {
            return when {
                address.isContact -> {
                    Contact(address.contactIdentifier())
                }
                address.isClosedGroup -> {
                    val groupID = address.toGroupString()
                    val groupPublicKey = GroupUtil.doubleDecodeGroupID(groupID).toHexString()
                    ClosedGroup(groupPublicKey)
                }
                address.isOpenGroup -> {
                    val storage = MessagingModuleConfiguration.shared.storage
                    val threadID = storage.getThreadId(address)!!
                    storage.getOpenGroup(threadID)?.let {
                        OpenGroup(roomToken = it.room, server = it.server, fileIds = fileIds)
                    } ?: throw Exception("Missing open group for thread with ID: $threadID.")
                }
                address.isOpenGroupInbox -> {
                    val groupInboxId = GroupUtil.getDecodedGroupID(address.serialize()).split(".")
                    OpenGroupInbox(
                        groupInboxId.dropLast(2).joinToString("."),
                        groupInboxId.dropLast(1).last(),
                        groupInboxId.last()
                    )
                }
                else -> {
                    throw Exception("TODO: Handle legacy closed groups.")
                }
            }
        }
    }
}