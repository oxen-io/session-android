package org.session.libsession.messaging.utilities

import android.content.Context
import com.squareup.phrase.Phrase
import org.session.libsession.R
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.calls.CallMessageType
import org.session.libsession.messaging.calls.CallMessageType.CALL_FIRST_MISSED
import org.session.libsession.messaging.calls.CallMessageType.CALL_INCOMING
import org.session.libsession.messaging.calls.CallMessageType.CALL_MISSED
import org.session.libsession.messaging.calls.CallMessageType.CALL_OUTGOING
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.sending_receiving.data_extraction.DataExtractionNotificationInfoMessage
import org.session.libsession.utilities.Address
import org.session.libsession.messaging.sending_receiving.data_extraction.DataExtractionNotificationInfoMessage.Kind.MEDIA_SAVED
import org.session.libsession.messaging.sending_receiving.data_extraction.DataExtractionNotificationInfoMessage.Kind.SCREENSHOT
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.getExpirationTypeDisplayValue
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.truncateIdForDisplay

object UpdateMessageBuilder {
    private val TAG = "UpdateMessageBuilder"


    private const val FIRST = "first"
    private const val SECOND = "second"
    private const val NUM_OTHERS = "number_others"
    private const val ADMIN = "admin"
    private const val GROUP = "group"
    private const val USER = "user"

    val storage = MessagingModuleConfiguration.shared.storage

    private fun getSenderName(senderId: String) = storage.getContactWithSessionID(senderId)
        ?.displayName(Contact.ContactContext.REGULAR)
        ?: truncateIdForDisplay(senderId)

    @JvmStatic
    fun buildGroupUpdateMessage(context: Context, updateMessageData: UpdateMessageData, senderId: String? = null, isOutgoing: Boolean = false, isInConversation: Boolean): CharSequence {
        val updateData = updateMessageData.kind
        if (updateData == null || !isOutgoing && senderId == null) return ""
        val senderName: String = if (isOutgoing) context.getString(R.string.MessageRecord_you)
        else getSenderName(senderId!!)

        return when (updateData) {
            is UpdateMessageData.Kind.GroupCreation -> {
                if (isOutgoing) context.getString(R.string.MessageRecord_you_created_a_new_group)
                else context.getString(R.string.MessageRecord_s_added_you_to_the_group, senderName)
            }
            is UpdateMessageData.Kind.GroupNameChange -> {
                if (isOutgoing) context.getString(R.string.MessageRecord_you_renamed_the_group_to_s, updateData.name)
                else context.getString(R.string.MessageRecord_s_renamed_the_group_to_s, senderName, updateData.name)
            }
            is UpdateMessageData.Kind.GroupMemberAdded -> {
                val members = updateData.updatedMembers.joinToString(", ", transform = ::getSenderName)
                if (isOutgoing) context.getString(R.string.MessageRecord_you_added_s_to_the_group, members)
                else context.getString(R.string.MessageRecord_s_added_s_to_the_group, senderName, members)
            }
            is UpdateMessageData.Kind.GroupMemberRemoved -> {
                val userPublicKey = storage.getUserPublicKey()!!
                // 1st case: you are part of the removed members
                return if (userPublicKey in updateData.updatedMembers) {
                    if (isOutgoing) context.getString(R.string.MessageRecord_left_group)
                    else context.getString(R.string.MessageRecord_you_were_removed_from_the_group)
                } else {
                    // 2nd case: you are not part of the removed members
                    val members = updateData.updatedMembers.joinToString(", ", transform = ::getSenderName)
                    if (isOutgoing) context.getString(R.string.MessageRecord_you_removed_s_from_the_group, members)
                    else context.getString(R.string.MessageRecord_s_removed_s_from_the_group, senderName, members)
                }
            }
            UpdateMessageData.Kind.GroupMemberLeft -> if (isOutgoing) {
                context.getString(R.string.MessageRecord_left_group)
            } else {
                Phrase.from(context, R.string.ConversationItem_group_action_left)
                    .put("user", senderName)
                    .format()!!
            }
            UpdateMessageData.Kind.GroupAvatarUpdated -> context.getString(R.string.ConversationItem_group_action_avatar_updated)
            is UpdateMessageData.Kind.GroupExpirationUpdated -> TODO()
            is UpdateMessageData.Kind.GroupMemberUpdated -> {
                val userPublicKey = storage.getUserPublicKey()!!
                val number = updateData.sessionIds.size
                val containsUser = updateData.sessionIds.contains(userPublicKey)
                when (updateData.type) {
                    UpdateMessageData.MemberUpdateType.ADDED -> {
                        when {
                            number == 1 && containsUser -> Phrase.from(context,
                                R.string.ConversationItem_group_member_you_added_single)
                                .format()
                            number == 1 -> Phrase.from(context,
                                R.string.ConversationItem_group_member_added_single)
                                .put(FIRST, context.youOrSender(updateData.sessionIds.first()))
                                .format()
                            number == 2 && containsUser -> Phrase.from(context,
                                R.string.ConversationItem_group_member_you_added_two)
                                .put(SECOND, context.youOrSender(updateData.sessionIds.first { it != userPublicKey }))
                                .format()
                            number == 2 -> Phrase.from(context,
                                R.string.ConversationItem_group_member_added_two)
                                .put(FIRST, context.youOrSender(updateData.sessionIds.first()))
                                .put(SECOND, context.youOrSender(updateData.sessionIds.last()))
                                .format()
                            containsUser -> Phrase.from(context,
                                R.string.ConversationItem_group_member_you_added_multiple)
                                .put(NUM_OTHERS, updateData.sessionIds.size - 1)
                                .format()
                            else -> Phrase.from(context,
                                R.string.ConversationItem_group_member_added_multiple)
                                .put(FIRST, context.youOrSender(updateData.sessionIds.first()))
                                .put(NUM_OTHERS, updateData.sessionIds.size - 1)
                                .format()
                        }
                    }
                    UpdateMessageData.MemberUpdateType.PROMOTED -> {
                        when {
                            number == 1 && containsUser -> context.getString(
                                R.string.ConversationItem_group_member_you_promoted_single
                            )
                            number == 1 -> Phrase.from(context,
                                R.string.ConversationItem_group_member_promoted_single)
                                .put(FIRST,context.youOrSender(updateData.sessionIds.first()))
                                .format()
                            number == 2 && containsUser -> Phrase.from(context,
                                R.string.ConversationItem_group_member_you_promoted_two)
                                .put(SECOND, context.youOrSender(updateData.sessionIds.first{ it != userPublicKey }))
                                .format()
                            number == 2 -> Phrase.from(context,
                                R.string.ConversationItem_group_member_promoted_two)
                                .put(FIRST, context.youOrSender(updateData.sessionIds.first()))
                                .put(SECOND, context.youOrSender(updateData.sessionIds.last()))
                                .format()
                            containsUser -> Phrase.from(context,
                                R.string.ConversationItem_group_member_you_promoted_multiple)
                                .put(NUM_OTHERS, updateData.sessionIds.size - 1)
                                .format()
                            else -> Phrase.from(context,
                                R.string.ConversationItem_group_member_promoted_multiple)
                                .put(FIRST, context.youOrSender(updateData.sessionIds.first()))
                                .put(NUM_OTHERS, updateData.sessionIds.size - 1)
                                .format()
                        }
                    }
                    UpdateMessageData.MemberUpdateType.REMOVED -> {
                        when {
                            number == 1 && containsUser -> context.getString(
                                R.string.ConversationItem_group_member_you_removed_single,
                            )
                            number == 1 -> Phrase.from(context,
                                R.string.ConversationItem_group_member_removed_single)
                                .put(FIRST, context.youOrSender(updateData.sessionIds.first()))
                                .format()
                            number == 2 && containsUser -> Phrase.from(context,
                                R.string.ConversationItem_group_member_you_removed_two)
                                .put(SECOND, context.youOrSender(updateData.sessionIds.first { it != userPublicKey }))
                                .format()
                            number == 2 -> Phrase.from(context,
                                R.string.ConversationItem_group_member_removed_two)
                                .put(FIRST, context.youOrSender(updateData.sessionIds.first()))
                                .put(SECOND, context.youOrSender(updateData.sessionIds.last()))
                                .format()
                            containsUser -> Phrase.from(context,
                                R.string.ConversationItem_group_member_you_removed_multiple)
                                .put(NUM_OTHERS, updateData.sessionIds.size - 1)
                                .format()
                            else -> Phrase.from(context,
                                R.string.ConversationItem_group_member_removed_multiple)
                                .put(FIRST, context.youOrSender(updateData.sessionIds.first()))
                                .put(NUM_OTHERS, updateData.sessionIds.size - 1)
                                .format()
                        }
                    }
                    null -> ""
                }
            }
            is UpdateMessageData.Kind.GroupInvitation -> {
                val invitingAdmin = Recipient.from(context, Address.fromSerialized(updateData.invitingAdmin), false)
                return if (invitingAdmin.name != null) {
                    Phrase.from(context, R.string.ConversationItem_group_member_invited_admin)
                        .put(ADMIN, invitingAdmin.name)
                        .put(GROUP, "Group")
                        .format()
                } else {
                    Phrase.from(context, R.string.ConversationItem_group_member_invited)
                        .put(GROUP, "Group")
                        .format()
                }
            }
            is UpdateMessageData.Kind.OpenGroupInvitation -> ""
            is UpdateMessageData.Kind.GroupMemberLeft -> {
                return if (isOutgoing) {
                    context.getString(R.string.MessageRecord_left_group)
                } else {
                    Phrase.from(context,R.string.ConversationItem_group_action_left)
                        .put(USER, senderName)
                        .format()
                }
            }
            is UpdateMessageData.Kind.GroupLeaving -> {
                return if (isOutgoing) {
                    context.getString(R.string.MessageRecord_leaving_group)
                } else {
                    ""
                }
            }
            is UpdateMessageData.Kind.GroupErrorQuit -> {
                return if (isInConversation) {
                    context.getString(R.string.MessageRecord_unable_leave_group)
                } else {
                    context.getString(R.string.MessageRecord_leave_group_error)
                }
            }
        }
    }

    fun Context.youOrSender(sessionId: String) = if (storage.getUserPublicKey() == sessionId) getString(R.string.MessageRecord_you) else getSenderName(sessionId)

    fun buildExpirationTimerMessage(
        context: Context,
        duration: Long,
        isGroup: Boolean,
        senderId: String? = null,
        isOutgoing: Boolean = false,
        timestamp: Long,
        expireStarted: Long
    ): String {
        if (!isOutgoing && senderId == null) return ""
        val senderName = if (isOutgoing) context.getString(R.string.MessageRecord_you) else getSenderName(senderId!!)
        return if (duration <= 0) {
            if (isOutgoing) context.getString(if (isGroup) R.string.MessageRecord_you_turned_off_disappearing_messages else R.string.MessageRecord_you_turned_off_disappearing_messages_1_on_1)
            else context.getString(if (isGroup) R.string.MessageRecord_s_turned_off_disappearing_messages else R.string.MessageRecord_s_turned_off_disappearing_messages_1_on_1, senderName)
        } else {
            val time = ExpirationUtil.getExpirationDisplayValue(context, duration.toInt())
            val action = context.getExpirationTypeDisplayValue(timestamp >= expireStarted)
            if (isOutgoing) context.getString(
                if (isGroup) R.string.MessageRecord_you_set_messages_to_disappear_s_after_s else R.string.MessageRecord_you_set_messages_to_disappear_s_after_s_1_on_1,
                time,
                action
            ) else context.getString(
                if (isGroup) R.string.MessageRecord_s_set_messages_to_disappear_s_after_s else R.string.MessageRecord_s_set_messages_to_disappear_s_after_s_1_on_1,
                senderName,
                time,
                action
            )
        }
    }

    fun buildDataExtractionMessage(context: Context, kind: DataExtractionNotificationInfoMessage.Kind, senderId: String? = null) = when (kind) {
        SCREENSHOT -> R.string.MessageRecord_s_took_a_screenshot
        MEDIA_SAVED -> R.string.MessageRecord_media_saved_by_s
    }.let { context.getString(it, getSenderName(senderId!!)) }

    fun buildCallMessage(context: Context, type: CallMessageType, sender: String): String =
        when (type) {
            CALL_INCOMING -> R.string.MessageRecord_s_called_you
            CALL_OUTGOING -> R.string.MessageRecord_called_s
            CALL_MISSED, CALL_FIRST_MISSED -> R.string.MessageRecord_missed_call_from
        }.let {
            context.getString(it, storage.getContactWithSessionID(sender)?.displayName(Contact.ContactContext.REGULAR) ?: sender)
        }

    fun buildGroupModalMessage(
        context: Context,
        updateMessageData: UpdateMessageData,
        serialize: String
    ): CharSequence? {
        return updateMessageData.kind?.takeIf {
            it is UpdateMessageData.Kind.GroupMemberUpdated
                    && it.type == UpdateMessageData.MemberUpdateType.ADDED
        }?.let { kind ->
            val members = (kind as UpdateMessageData.Kind.GroupMemberUpdated).sessionIds
            val userPublicKey = storage.getUserPublicKey()!!
            val number = members.size
            val containsUser = members.contains(userPublicKey)
            when {
                number == 1 && containsUser -> Phrase.from(context,
                    R.string.ConversationItem_group_member_you_added_single_modal)
                    .put(FIRST, context.youOrSender(kind.sessionIds.first()))
                    .format()
                number == 1 -> Phrase.from(context,
                    R.string.ConversationItem_group_member_added_single_modal)
                    .put(FIRST, context.youOrSender(kind.sessionIds.first()))
                    .format()
                number == 2 && containsUser -> Phrase.from(context,
                    R.string.ConversationItem_group_member_you_added_two_modal)
                    .put(FIRST, context.youOrSender(kind.sessionIds.first()))
                    .put(SECOND, context.youOrSender(kind.sessionIds.last()))
                    .format()
                number == 2 -> Phrase.from(context,
                    R.string.ConversationItem_group_member_added_two_modal)
                    .put(FIRST, context.youOrSender(kind.sessionIds.first()))
                    .put(SECOND, context.youOrSender(kind.sessionIds.last()))
                    .format()
                containsUser -> Phrase.from(context,
                    R.string.ConversationItem_group_member_you_added_multiple_modal)
                    .put(FIRST, context.youOrSender(kind.sessionIds.first()))
                    .put(NUM_OTHERS, kind.sessionIds.size - 1)
                    .format()
                else -> Phrase.from(context,
                    R.string.ConversationItem_group_member_added_multiple_modal)
                    .put(FIRST, context.youOrSender(kind.sessionIds.first()))
                    .put(NUM_OTHERS, kind.sessionIds.size - 1)
                    .format()
            }
        }
    }
}
