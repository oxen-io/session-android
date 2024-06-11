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

    // Keys for Phrase library string substitutions - do not include the curly braces!
    const val COUNT_KEY                      = "count"
    const val DISAPPEARING_MESSAGES_TYPE_KEY = "disappearing_messages_type"
    const val GROUP_NAME_KEY                 = "group_name"
    const val MEMBERS_KEY                    = "members"
    const val NAME_KEY                       = "name"
    const val OTHER_NAME_KEY                 = "other_name"
    const val TIME_KEY                       = "time"

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
                    Phrase.from(context, R.string.groupNameUpdatedBy)
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
        isGroup: Boolean, // ACL TODO: Does this include communities? (i.e., open groups) - probably??? Do some testing!
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
                // ACL TODO - Do we want one of these to be "has turned THEIR disappearing messages off?" - likely the 1-on-1?
                val stringId = if (isGroup) R.string.disappearingMessagesTurnedOff else // If you can turn disappearing msgs on/off in group then ur admin?? Check!
                                            R.string.disappearingMessagesTurnedOff
                Phrase.from(context, stringId).put(NAME_KEY, senderName).format().toString()
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
        val senderName = storage.getContactWithSessionID(senderId)?.displayName(Contact.ContactContext.REGULAR) ?: senderId

        return when (type) {
            CALL_INCOMING -> Phrase.from(context, R.string.callsCalledYou).put(NAME_KEY, senderName).format().toString()
            CALL_OUTGOING -> Phrase.from(context, R.string.callsYouCalled).put(NAME_KEY, senderName).format().toString()

            CALL_MISSED, CALL_FIRST_MISSED -> Phrase.from(context, R.string.callsMissedCallFrom)
                .put(NAME_KEY, senderName)
                .format().toString()
        }
    }
}
