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
    class OpenGroupV2(var room: String, var server: String) : Destination() {
        internal constructor(): this("", "")
    }

    companion object {

        fun from(address: Address): Destination {
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
                    when (val openGroup = storage.getOpenGroup(threadID)) {
                        is org.session.libsession.messaging.open_groups.OpenGroup
                            -> Destination.OpenGroupV2(openGroup.room, openGroup.server)
                        else -> throw Exception("Missing open group for thread with ID: $threadID.")
                    }
                }
                else -> {
                    throw Exception("TODO: Handle legacy closed groups.")
                }
            }
        }
    }
}