package org.session.libsession.utilities.recipients

enum class MessageType {
    ONE_ON_ONE, LEGACY_GROUP, GROUPS_V2, NOTE_TO_SELF, COMMUNITY
}

fun Recipient.getType(): MessageType =
    when{
        isCommunityRecipient -> MessageType.COMMUNITY
        isLocalNumber -> MessageType.NOTE_TO_SELF
        isLegacyGroupRecipient -> MessageType.LEGACY_GROUP
        isGroupV2Recipient -> MessageType.GROUPS_V2
        else -> MessageType.ONE_ON_ONE
    }