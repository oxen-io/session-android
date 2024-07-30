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
import org.session.libsignal.utilities.Log
import org.session.libsession.utilities.StringSubstitutionConstants.COUNT_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.DISAPPEARING_MESSAGES_TYPE_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.GROUP_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.MEMBERS_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.OTHER_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.TIME_KEY

object UpdateMessageBuilder {
    const val TAG = "UpdateMessageBuilder"


    private const val FIRST = "first"
    private const val SECOND = "second"
    private const val NUM_OTHERS = "number_others"
    private const val ADMIN = "admin"
    private const val GROUP = "group"
    private const val USER = "user"

    val storage = MessagingModuleConfiguration.shared.storage

    private fun getSenderName(senderId: String) = storage.getContactWithAccountID(senderId)
        ?.displayName(Contact.ContactContext.REGULAR)
        ?: truncateIdForDisplay(senderId)

    @JvmStatic
    fun buildGroupUpdateMessage(context: Context, updateMessageData: UpdateMessageData, senderId: String? = null, isOutgoing: Boolean = false, isInConversation: Boolean): CharSequence {
        val updateData = updateMessageData.kind ?: return ""
        val senderName: String by lazy {
            senderId?.let(this::getSenderName).orEmpty()
        }

        return when (updateData) {
            // --- Group created or joined ---
            is UpdateMessageData.Kind.GroupCreation -> {
                if (isOutgoing) context.getString(R.string.disappearingMessagesNewGroup)
                else Phrase.from(context, R.string.disappearingMessagesAddedYou)
                    .put(NAME_KEY, senderName)
                    .format().toString()
            }

            // --- Group name changed ---
            is UpdateMessageData.Kind.GroupNameChange -> {
                if (isOutgoing) {
                        Phrase.from(context, R.string.groupNameNew)
                        .put(GROUP_NAME_KEY, updateData.name)
                        .format().toString()
                }
                else {
                    Phrase.from(context, R.string.groupNameUpdatedBy)
                        .put(NAME_KEY, senderName)
                        .put(GROUP_NAME_KEY, updateData.name)
                        .format().toString()
                }
            }

            // --- Group member(s) were added ---
            is UpdateMessageData.Kind.GroupMemberAdded -> {
                val members = updateData.updatedMembers.joinToString(", ", transform = ::getSenderName)

                // You added these members
                if (isOutgoing) {
                    Phrase.from(context, R.string.groupYouAdded)
                        .put(MEMBERS_KEY, members)
                        .format().toString()
                }
                // Someone else added these members
                else {
                    Phrase.from(context, R.string.groupNameAdded)
                        .put(NAME_KEY,senderName)
                        .put(MEMBERS_KEY, members)
                        .format().toString()
                }
            }

            // --- Group member(s) removed ---
            is UpdateMessageData.Kind.GroupMemberRemoved -> {
                val userPublicKey = storage.getUserPublicKey()!!

                // 1st case: you are part of the removed members
                return if (userPublicKey in updateData.updatedMembers) {
                    if (isOutgoing) context.getString(R.string.groupMemberYouLeft) // You chose to leave
                    else Phrase.from(context, R.string.groupRemovedYou)            // You were forced to leave
                            .put(GROUP_NAME_KEY, updateData.groupName)
                            .format().toString()
                }
                else // 2nd case: you are not part of the removed members
                {
                    val members = updateData.updatedMembers.joinToString(", ", transform = ::getSenderName)

                    // a.) You are the person doing the removing of one or more members
                    if (isOutgoing) {
                        when (updateData.updatedMembers.size) {
                            0 -> {
                                Log.w(TAG, "Somehow you asked to remove zero members.")
                                "" // Return an empty string - we don't want to show the error in the conversation
                                }
                            1 -> Phrase.from(context, R.string.groupRemoved)
                                .put(NAME_KEY, getSenderName(updateData.updatedMembers.elementAt(0)))
                                .format().toString()
                            2 -> Phrase.from(context, R.string.groupRemovedTwo)
                                .put(NAME_KEY, getSenderName(updateData.updatedMembers.elementAt(0)))
                                .put(OTHER_NAME_KEY, getSenderName(updateData.updatedMembers.elementAt(1)))
                                .format().toString()
                            else -> Phrase.from(context, R.string.groupRemovedMore)
                                    .put(NAME_KEY, getSenderName(updateData.updatedMembers.elementAt(0)))
                                    .put(COUNT_KEY, updateData.updatedMembers.size - 1)
                                    .format().toString()
                        }
                    }
                    else // b.) Someone else is the person doing the removing of one or more members
                    {
                        // ACL TODO: Remove below line when confirmed that we aren't mentioning WHO removed anyone anymore.. or don't if we still are!
                        //context.getString(R.string.MessageRecord_s_removed_s_from_the_group, senderName, members)

                        // Note: I don't think we're doing "Alice removed Bob from the group"-type
                        // messages anymore - just "Bob was removed from the group" - so this block
                        // is identical to the one above, but I'll leave it like this until I can
                        // confirm that this is the case.
                        when (updateData.updatedMembers.size) {
                            0 -> {
                                Log.w(TAG, "Somehow someone else asked to remove zero members.")
                                "" // Return an empty string - we don't want to show the error in the conversation
                            }
                            1 -> Phrase.from(context, R.string.groupRemoved)
                                .put(NAME_KEY, getSenderName(updateData.updatedMembers.elementAt(0)))
                                .format().toString()
                            2 -> Phrase.from(context, R.string.groupRemovedTwo)
                                .put(NAME_KEY, getSenderName(updateData.updatedMembers.elementAt(0)))
                                .put(OTHER_NAME_KEY, getSenderName(updateData.updatedMembers.elementAt(1)))
                                .format().toString()
                            else -> Phrase.from(context, R.string.groupRemovedMore)
                                .put(NAME_KEY, getSenderName(updateData.updatedMembers.elementAt(0)))
                                .put(COUNT_KEY, updateData.updatedMembers.size - 1)
                                .format().toString()
                        }
                    }
                }
            }
            is UpdateMessageData.Kind.GroupMemberLeft -> {
                if (isOutgoing) context.getString(R.string.groupMemberYouLeft)
                else {
                    when (updateData.updatedMembers.size) {
                        0 -> {
                            Log.w(TAG, "Somehow zero members left the group.")
                            "" // Return an empty string - we don't want to show the error in the conversation
                        }
                        1 -> Phrase.from(context, R.string.groupMemberLeft)
                            .put(NAME_KEY, getSenderName(updateData.updatedMembers.elementAt(0)))
                            .format().toString()
                        2 -> Phrase.from(context, R.string.groupMemberLeftTwo)
                            .put(NAME_KEY, getSenderName(updateData.updatedMembers.elementAt(0)))
                            .put(OTHER_NAME_KEY, getSenderName(updateData.updatedMembers.elementAt(1)))
                            .format().toString()
                        else -> Phrase.from(context, R.string.groupMemberLeftMore)
                            .put(NAME_KEY, getSenderName(updateData.updatedMembers.elementAt(0)))
                            .put(COUNT_KEY, updateData.updatedMembers.size - 1)
                            .format().toString()
                    }
                }
            }
            is UpdateMessageData.Kind.GroupAvatarUpdated -> context.getString(R.string.ConversationItem_group_action_avatar_updated)
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
            is UpdateMessageData.Kind.GroupKicked -> {
                return context.getString(R.string.MessageRecord_kicked)
            }
        }
    }

    fun Context.youOrSender(sessionId: String) = if (storage.getUserPublicKey() == sessionId) getString(R.string.MessageRecord_you) else getSenderName(sessionId)

    fun buildExpirationTimerMessage(
        context: Context,
        duration: Long,
        isGroup: Boolean, // ACL TODO: Does this include communities? (i.e., open groups)?
        senderId: String? = null,
        isOutgoing: Boolean = false,
        timestamp: Long,
        expireStarted: Long
    ): String {
        if (!isOutgoing && senderId == null) {
            Log.w(TAG, "buildExpirationTimerMessage: Cannot build for outgoing message when senderId is null.")
            return ""
        }

        val senderName = if (isOutgoing) context.getString(R.string.you) else getSenderName(senderId!!)

        // Case 1.) Disappearing messages have been turned off..
        if (duration <= 0) {
            // ..by you..
            return if (isOutgoing) {
                context.getString(R.string.disappearingMessagesTurnedOffYou)
            }
            else // ..or by someone else.
            {
                Phrase.from(context, R.string.disappearingMessagesTurnedOff)
                    .put(NAME_KEY, senderName)
                    .format().toString()
            }
        }

        // Case 2.) Disappearing message settings have been changed but not turned off.
        val time = ExpirationUtil.getExpirationDisplayValue(context, duration.toInt())
        val action = context.getExpirationTypeDisplayValue(timestamp >= expireStarted)

        //..by you..
        if (isOutgoing) {
            return if (isGroup) {
                Phrase.from(context, R.string.disappearingMessagesSetYou)
                    .put(TIME_KEY, time)
                    .put(DISAPPEARING_MESSAGES_TYPE_KEY, action)
                    .format().toString()
            } else // 1-on-1 conversation
            {
                Phrase.from(context, R.string.disappearingMessagesUpdatedYours)
                    .put(TIME_KEY, time)
                    .put(DISAPPEARING_MESSAGES_TYPE_KEY, action)
                    .format().toString()
            }
        }
        else // ..or by someone else.
        {
            return Phrase.from(context, R.string.disappearingMessagesSet)
                .put(NAME_KEY, senderName)
                .put(TIME_KEY, time)
                .put(DISAPPEARING_MESSAGES_TYPE_KEY, action)
                .format().toString()
        }
    }

    fun buildDataExtractionMessage(context: Context,
                                   kind: DataExtractionNotificationInfoMessage.Kind,
                                   senderId: String? = null): String {

        val senderName = if (senderId != null) getSenderName(senderId) else context.getString(R.string.unknown)

        return when (kind) {
            SCREENSHOT  -> Phrase.from(context, R.string.screenshotTaken)
                .put(NAME_KEY, senderName)
                .format().toString()

            MEDIA_SAVED -> Phrase.from(context, R.string.attachmentsMediaSaved)
                .put(NAME_KEY, senderName)
                .format().toString()
        }
    }

    fun buildCallMessage(context: Context, type: CallMessageType, senderId: String): String {
        val senderName = storage.getContactWithAccountID(senderId)?.displayName(Contact.ContactContext.REGULAR) ?: senderId

        return when (type) {
            CALL_INCOMING -> Phrase.from(context, R.string.callsCalledYou).put(NAME_KEY, senderName).format().toString()
            CALL_OUTGOING -> Phrase.from(context, R.string.callsYouCalled).put(NAME_KEY, senderName).format().toString()
            CALL_MISSED, CALL_FIRST_MISSED -> Phrase.from(context, R.string.callsMissedCallFrom).put(NAME_KEY, senderName).format().toString()
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
