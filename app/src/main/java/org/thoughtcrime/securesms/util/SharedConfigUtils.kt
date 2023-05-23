package org.thoughtcrime.securesms.util

import network.loki.messenger.libsession_util.ConversationVolatileConfig
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.utilities.GroupUtil
import org.thoughtcrime.securesms.database.model.ThreadRecord

fun ConversationVolatileConfig.getConversationUnread(thread: ThreadRecord): Boolean {
    val recipient = thread.recipient
    if (recipient.isContactRecipient) {
        return getOneToOne(recipient.address.serialize())?.unread == true
    } else if (recipient.isClosedGroupRecipient) {
        return getLegacyClosedGroup(GroupUtil.doubleDecodeGroupId(recipient.address.toGroupString()))?.unread == true
    } else if (recipient.isOpenGroupRecipient) {
        val openGroup = MessagingModuleConfiguration.shared.storage.getOpenGroup(thread.threadId) ?: return false
        return getCommunity(openGroup.server, openGroup.room)?.unread == true
    }
    return false
}