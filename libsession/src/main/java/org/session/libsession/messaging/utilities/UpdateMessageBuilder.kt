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
import org.session.libsession.messaging.sending_receiving.data_extraction.DataExtractionNotificationInfoMessage.Kind.MEDIA_SAVED
import org.session.libsession.messaging.sending_receiving.data_extraction.DataExtractionNotificationInfoMessage.Kind.SCREENSHOT
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.getExpirationTypeDisplayValue
import org.session.libsession.utilities.truncateIdForDisplay
import org.session.libsignal.utilities.Log

object UpdateMessageBuilder {

    const val TAG = "libsession"

    // Keys for Phrase library substitution
    const val COUNT_KEY      = "count"
    const val MEMBERS_KEY    = "members"
    const val NAME_KEY       = "name"
    const val OTHER_NAME_KEY = "other_name"
    const val GROUP_NAME_KEY = "group_name"

    val storage = MessagingModuleConfiguration.shared.storage

    private fun getSenderName(senderId: String) = storage.getContactWithSessionID(senderId)
        ?.displayName(Contact.ContactContext.REGULAR)
        ?: truncateIdForDisplay(senderId)

    fun buildGroupUpdateMessage(context: Context, updateMessageData: UpdateMessageData, senderId: String? = null, isOutgoing: Boolean = false): String {
        val updateData = updateMessageData.kind
        if (updateData == null || !isOutgoing && senderId == null) return ""
        val senderName: String = if (isOutgoing) context.getString(R.string.you) else getSenderName(senderId!!)

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
                    Phrase.from(context, R.string.disappearingMessagesRenamedGroup)
                        .put(NAME_KEY, senderName)
                        .put(GROUP_NAME_KEY, updateData.name)
                        .format().toString()
                }
            }

            // --- Group member(s) were added ---
            is UpdateMessageData.Kind.GroupMemberAdded -> {
                val members = updateData.updatedMembers.joinToString(", ", transform = ::getSenderName)
                if (isOutgoing) {
                    Phrase.from(context, R.string.groupYouAdded)
                        .put(MEMBERS_KEY, members)
                        .format().toString()
                }
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
                    if (isOutgoing) context.getString(R.string.groupMemberYouLeft)
                    else Phrase.from(context, R.string.groupRemovedYou)
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
                                .put(NAME_KEY, updateData.updatedMembers.elementAt(0))
                                .format().toString()
                            2 -> Phrase.from(context, R.string.groupRemovedTwo)
                                .put(NAME_KEY, updateData.updatedMembers.elementAt(0))
                                .put(OTHER_NAME_KEY, updateData.updatedMembers.elementAt(1))
                                .format().toString()
                            else -> Phrase.from(context, R.string.groupRemovedMore)
                                    .put(NAME_KEY, updateData.updatedMembers.elementAt(0))
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
                                .put(NAME_KEY, updateData.updatedMembers.elementAt(0))
                                .format().toString()
                            2 -> Phrase.from(context, R.string.groupRemovedTwo)
                                .put(NAME_KEY, updateData.updatedMembers.elementAt(0))
                                .put(OTHER_NAME_KEY, updateData.updatedMembers.elementAt(1))
                                .format().toString()
                            else -> Phrase.from(context, R.string.groupRemovedMore)
                                .put(NAME_KEY, updateData.updatedMembers.elementAt(0))
                                .put(COUNT_KEY, updateData.updatedMembers.size - 1)
                                .format().toString()
                        }
                    }
                }
            }

            // --- Group members left ---
            is UpdateMessageData.Kind.GroupMemberLeft -> {
                if (isOutgoing) context.getString(R.string.groupMemberYouLeft)
                else {
                    when (updateData.updatedMembers.size) {
                        0 -> {
                            Log.w(TAG, "Somehow zero members left the group.")
                            "" // Return an empty string - we don't want to show the error in the conversation
                        }
                        1 -> Phrase.from(context, R.string.groupMemberLeft)
                            .put(NAME_KEY, updateData.updatedMembers.elementAt(0))
                            .format().toString()
                        2 -> Phrase.from(context, R.string.groupMemberLeftTwo)
                            .put(NAME_KEY, updateData.updatedMembers.elementAt(0))
                            .put(OTHER_NAME_KEY, updateData.updatedMembers.elementAt(1))
                            .format().toString()
                        else -> Phrase.from(context, R.string.groupMemberLeftMore)
                            .put(NAME_KEY, updateData.updatedMembers.elementAt(0))
                            .put(COUNT_KEY, updateData.updatedMembers.size - 1)
                            .format().toString()
                    }
                }
            }
            else -> return ""
        }
    }

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
        val senderName = if (isOutgoing) context.getString(R.string.you) else getSenderName(senderId!!)
        return if (duration <= 0) {
            if (isOutgoing) context.getString(if (isGroup) R.string.MessageRecord_you_turned_off_disappearing_messages else R.string.MessageRecord_you_turned_off_disappearing_messages_1_on_1)
            else context.getString(if (isGroup) R.string.MessageRecord_s_turned_off_disappearing_messages else R.string.MessageRecord_s_turned_off_disappearing_messages_1_on_1, senderName)
        } else {
            val time = ExpirationUtil.getExpirationDisplayValue(context, duration.toInt())
            val action = context.getExpirationTypeDisplayValue(timestamp >= expireStarted)
            if (isOutgoing) context.getString( //disappearingMessagesSetYou
                if (isGroup) R.string.MessageRecord_s_changed_messages_to_disappear_s_after_s else R.string.MessageRecord_you_set_messages_to_disappear_s_after_s_1_on_1,
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
}
