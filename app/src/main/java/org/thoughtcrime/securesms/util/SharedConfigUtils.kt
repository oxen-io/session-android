package org.thoughtcrime.securesms.util

import network.loki.messenger.libsession_util.ReadableConversationVolatileConfig
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.utilities.IdPrefix
import org.thoughtcrime.securesms.database.model.ThreadRecord

fun ReadableConversationVolatileConfig.getConversationUnread(thread: ThreadRecord): Boolean {
    val recipient = thread.recipient
    if (recipient.isContactRecipient
        && recipient.isCommunityInboxRecipient
        && recipient.address.serialize().startsWith(IdPrefix.STANDARD.value)) {
        return getOneToOne(recipient.address.serialize())?.unread == true
    } else if (recipient.isGroupV2Recipient) {
        return getClosedGroup(recipient.address.serialize())?.unread == true
    } else if (recipient.isLegacyGroupRecipient) {
        return getLegacyClosedGroup(GroupUtil.doubleDecodeGroupId(recipient.address.toGroupString()))?.unread == true
    } else if (recipient.isCommunityRecipient) {
        val openGroup = MessagingModuleConfiguration.shared.storage.getOpenGroup(thread.threadId) ?: return false
        return getCommunity(openGroup.server, openGroup.room)?.unread == true
    }
    return false
}