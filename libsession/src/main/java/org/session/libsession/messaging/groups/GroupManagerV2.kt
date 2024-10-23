package org.session.libsession.messaging.groups

import org.session.libsession.messaging.messages.control.GroupUpdated
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.protos.SignalServiceProtos.DataMessage.GroupUpdateDeleteMemberContentMessage
import org.session.libsignal.utilities.AccountId

/**
 * Business logic handling group v2 operations like inviting members,
 * removing members, promoting members, leaving groups, etc.
 */
interface GroupManagerV2 {
    suspend fun createGroup(
        groupName: String,
        groupDescription: String,
        members: Set<AccountId>
    ): Recipient

    suspend fun inviteMembers(
        group: AccountId,
        newMembers: List<AccountId>,
        shareHistory: Boolean
    )

    suspend fun removeMembers(
        groupAccountId: AccountId,
        removedMembers: List<AccountId>,
        removeMessages: Boolean
    )

    /**
     * Remove all messages from the group for the given members.
     *
     * This will delete all messages locally, and, if the user is an admin, remotely as well.
     *
     * Note: unlike [handleDeleteMemberContent], [requestMessageDeletion], this method
     * does not try to validate the validity of the request, it also does not ask other members
     * to delete the messages. It simply removes what it can.
     */
    suspend fun removeMemberMessages(
        groupAccountId: AccountId,
        members: List<AccountId>
    )

    suspend fun handleMemberLeft(message: GroupUpdated, group: AccountId)

    suspend fun leaveGroup(groupId:
                           AccountId, deleteOnLeave: Boolean)

    suspend fun promoteMember(group: AccountId, members: List<AccountId>)

    suspend fun handleInvitation(
        groupId: AccountId,
        groupName: String,
        authData: ByteArray,
        inviter: AccountId,
        inviteMessageHash: String,
        inviteMessageTimestamp: Long,
    )

    suspend fun handlePromotion(
        groupId: AccountId,
        groupName: String,
        adminKey: ByteArray,
        promoter: AccountId,
        promoteMessageHash: String,
        promoteMessageTimestamp: Long,
    )

    suspend fun respondToInvitation(groupId: AccountId, approved: Boolean): Unit?

    suspend fun handleInviteResponse(groupId: AccountId, sender: AccountId, approved: Boolean)

    suspend fun handleKicked(groupId: AccountId)

    suspend fun setName(groupId: AccountId, newName: String)

    /**
     * Send a request to the group to delete the given messages.
     *
     * It can be called by a regular member who wishes to delete their own messages.
     * It can also called by an admin, who can delete any messages from any member.
     */
    suspend fun requestMessageDeletion(groupId: AccountId, messageHashes: List<String>)

    /**
     * Handle a request to delete a member's content from the group. This is called when we receive
     * a message from the server that a member's content needs to be deleted. (usually sent by
     * [requestMessageDeletion], for example)
     *
     * In contrast to [removeMemberMessages], where it will remove the messages blindly, this method
     * will check if the right conditions are met before removing the messages.
     */
    suspend fun handleDeleteMemberContent(
        groupId: AccountId,
        deleteMemberContent: GroupUpdateDeleteMemberContentMessage,
        sender: AccountId,
        senderIsVerifiedAdmin: Boolean,
    )
}