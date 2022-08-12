package org.thoughtcrime.securesms.conversation.v2.menus

import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import network.loki.messenger.R
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.groups.OpenGroupManager

object ConversationMenuItemHelper {

    fun onPrepareMenu(menu: Menu, inflater: MenuInflater, message: MessageRecord, context: Context) {
        inflater.inflate(R.menu.menu_conversation_item_action, menu)
        // Prepare
        val containsControlMessage = message.isUpdate
        val hasText = message.body.isNotEmpty()
        val openGroup = DatabaseComponent.get(context).lokiThreadDatabase().getOpenGroupChat(message.threadId)
        val thread = DatabaseComponent.get(context).threadDatabase().getRecipientForThreadId(message.threadId)!!
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
        fun userCanDeleteSelectedItems(): Boolean {
            if (openGroup == null) {
                return message.isOutgoing || !message.isOutgoing }
            if (message.isOutgoing) {
                return true
            }
            return OpenGroupManager.isUserModerator(context, openGroup.groupId, userPublicKey)
        }
        fun userCanBanSelectedUsers(): Boolean {
            if (openGroup == null) { return false }
            if (message.isOutgoing) {
                return false
            } // Users can't ban themselves
            return OpenGroupManager.isUserModerator(context, openGroup.groupId, userPublicKey)
        }
        // Delete message
        menu.findItem(R.id.menu_context_delete_message).isVisible = userCanDeleteSelectedItems()
        // Ban user
        menu.findItem(R.id.menu_context_ban_user).isVisible = userCanBanSelectedUsers()
        // Ban and delete all
        menu.findItem(R.id.menu_context_ban_and_delete_all).isVisible = userCanBanSelectedUsers()
        // Copy message text
        menu.findItem(R.id.menu_context_copy).isVisible = !containsControlMessage && hasText
        // Copy Session ID
        menu.findItem(R.id.menu_context_copy_public_key).isVisible =
            (thread.isGroupRecipient && !thread.isOpenGroupRecipient && message.recipient.address.toString() != userPublicKey)
        // Message detail
        menu.findItem(R.id.menu_message_details).isVisible = message.isFailed
        // Resend
        menu.findItem(R.id.menu_context_resend).isVisible = message.isFailed
        // Save media
        menu.findItem(R.id.menu_context_save_attachment).isVisible = (message.isMms && (message as MediaMmsMessageRecord).containsMediaSlide())
        // Reply
        menu.findItem(R.id.menu_context_reply).isVisible = (!message.isPending && !message.isFailed)
    }

    fun onMenuItemSelected(item: MenuItem, message: MessageRecord, delegate: ConversationActionModeCallbackDelegate) {
        val selectedItems = setOf(message)
        when (item.itemId) {
            R.id.menu_context_select_message -> delegate.selectMessages(selectedItems)
            R.id.menu_context_delete_message -> delegate.deleteMessages(selectedItems)
            R.id.menu_context_ban_user -> delegate.banUser(selectedItems)
            R.id.menu_context_ban_and_delete_all -> delegate.banAndDeleteAll(selectedItems)
            R.id.menu_context_copy -> delegate.copyMessages(selectedItems)
            R.id.menu_context_copy_public_key -> delegate.copySessionID(selectedItems)
            R.id.menu_context_resend -> delegate.resendMessage(selectedItems)
            R.id.menu_message_details -> delegate.showMessageDetail(selectedItems)
            R.id.menu_context_save_attachment -> delegate.saveAttachment(selectedItems)
            R.id.menu_context_reply -> delegate.reply(selectedItems)
        }
    }

    @JvmStatic
    fun userCanDeleteSelectedItems(context: Context, message: MessageRecord, openGroup: OpenGroup?, userPublicKey: String): Boolean {
        if (openGroup  == null) return message.isOutgoing || !message.isOutgoing
        if (message.isOutgoing) return true
        return OpenGroupManager.isUserModerator(context, openGroup.groupId, userPublicKey)
    }

    @JvmStatic
    fun userCanBanSelectedUsers(context: Context, message: MessageRecord, openGroup: OpenGroup?, userPublicKey: String): Boolean {
        if (openGroup == null)  return false
        if (message.isOutgoing) return false // Users can't ban themselves
        return OpenGroupManager.isUserModerator(context, openGroup.groupId, userPublicKey)
    }

}
