package org.session.libsession.messaging.open_groups

sealed class Endpoint(val value: String) {

    object Onion : Endpoint("oxen/v4/lsrpc")
    object Batch : Endpoint("batch")
    object Sequence : Endpoint("sequence")
    object Capabilities : Endpoint("capabilities")

    // Rooms

    object Rooms : Endpoint("rooms")
    data class Room(val roomToken: String) : Endpoint("room/$roomToken")
    data class RoomPollInfo(val roomToken: String, val infoUpdated: Long) :
        Endpoint("room/$roomToken/pollInfo/$infoUpdated")

    // Messages

    data class RoomMessage(val roomToken: String) :
        Endpoint("room/$roomToken/message")

    data class RoomMessageIndividual(val roomToken: String, val messageId) :
        Endpoint("room/$roomToken/message/$messageId")

    data class RoomMessagesRecent(val roomToken: String) :
        Endpoint("room/$roomToken/messages/recent")

    data class RoomMessagesBefore(val roomToken: String, val messageId: Long) :
        Endpoint("room/$roomToken/messages/before/$messageId")

    data class RoomMessagesSince(val roomToken: String, val seqNo) :
        Endpoint("room/$roomToken/messages/since/$seqNo")

    data class RoomDeleteMessages(val roomToken: String, val sessionId) :
        Endpoint("room/$roomToken/all/$sessionId")

    // Pinning

    data class RoomPinMessage(val roomToken: String, val messageId) :
        Endpoint("room/$roomToken/pin/$messageId")

    data class RoomUnpinMessage(val roomToken: String, val messageId) :
        Endpoint("room/$roomToken/unpin/$messageId")

    data class RoomUnpinAll(val roomToken: String) :
        Endpoint("room/$roomToken/unpin/all")

    // Files

    data class RoomFile(val roomToken: String) : Endpoint("room/$roomToken/file")
    data class RoomFileIndividual(
        val roomToken: String,
        val fileId: Long
    ) : Endpoint("room/$roomToken/file/$fileId")

    // Inbox/Outbox (Message Requests)

    object Inbox : Endpoint("inbox")
    data class InboxSince(val id: Long) : Endpoint("inbox/since/$id")
    data class InboxFor(val sessionId: String) : Endpoint("inbox/$sessionId")

    object Outbox : Endpoint("outbox")
    data class OutboxSince(val id: Long) : Endpoint("outbox/since/$id")

    // Users

    data class UserBan(val sessionId: String) : Endpoint("user/$sessionId/ban")
    data class UserUnban(val sessionId: String) : Endpoint("user/$sessionId/unban")
    data class UserModerator(val sessionId: String) : Endpoint("user/$sessionId/moderator")

}
