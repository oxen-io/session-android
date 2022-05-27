package org.thoughtcrime.securesms.conversation.v2.menus

import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import network.loki.messenger.R
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.session.libsession.messaging.open_groups.OpenGroupV2
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent

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
            return OpenGroupAPIV2.isUserModerator(userPublicKey, openGroup.room, openGroup.server)
        }
        fun userCanBanSelectedUsers(): Boolean {
            if (openGroup == null) { return false }
            if (message.isOutgoing) {
                return false
            } // Users can't ban themselves
            return OpenGroupAPIV2.isUserModerator(userPublicKey, openGroup.room, openGroup.server)
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
        when (item.itemId) {
            R.id.menu_context_delete_message -> delegate.deleteMessage(message)
            R.id.menu_context_ban_user -> delegate.banUser(message)
            R.id.menu_context_ban_and_delete_all -> delegate.banAndDeleteAll(message)
            R.id.menu_context_copy -> delegate.copyMessage(message)
            R.id.menu_context_copy_public_key -> delegate.copySessionID(message)
            R.id.menu_context_resend -> delegate.resendMessage(message)
            R.id.menu_message_details -> delegate.showMessageDetail(message)
            R.id.menu_context_save_attachment -> delegate.saveAttachment(message as MmsMessageRecord)
            R.id.menu_context_reply -> delegate.reply(message)
        }
    }

    @JvmStatic
    fun userCanDeleteSelectedItems(message: MessageRecord, openGroup: OpenGroupV2?, userPublicKey: String): Boolean {
        if (openGroup  == null) return message.isOutgoing || !message.isOutgoing
        if (message.isOutgoing) return true
        return OpenGroupAPIV2.isUserModerator(userPublicKey, openGroup.room, openGroup.server)
    }

    @JvmStatic
    fun userCanBanSelectedUsers(message: MessageRecord, openGroup: OpenGroupV2?, userPublicKey: String): Boolean {
        if (openGroup == null)  return false
        if (message.isOutgoing) return false // Users can't ban themselves
        return OpenGroupAPIV2.isUserModerator(userPublicKey, openGroup.room, openGroup.server)
    }

}

interface ConversationActionModeCallbackDelegate {

    fun deleteMessage(message: MessageRecord)
    fun banUser(message: MessageRecord)
    fun banAndDeleteAll(message: MessageRecord)
    fun copyMessage(message: MessageRecord)
    fun copySessionID(message: MessageRecord)
    fun resendMessage(message: MessageRecord)
    fun showMessageDetail(message: MessageRecord)
    fun saveAttachment(message: MmsMessageRecord)
    fun reply(message: MessageRecord)
}