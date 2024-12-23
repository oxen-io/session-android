package org.session.libsession.utilities.recipients

enum class MessageType {
    ONE_ON_ONE, LEGACY_GROUP, GROUPS_V2, NOTE_TO_SELF, COMMUNITY
}

fun Recipient.getType(): MessageType =
    when{
        isCommunityRecipient -> MessageType.COMMUNITY
        isLocalNumber -> MessageType.NOTE_TO_SELF
        isClosedGroupRecipient -> MessageType.LEGACY_GROUP //todo GROUPS V2 this property will change for groups v2. Check for legacyGroup here
        //isXXXXX -> RecipientType.GROUPS_V2 //todo GROUPS V2 this property will change for groups v2. Check for legacyGroup here
        else -> MessageType.ONE_ON_ONE
    }