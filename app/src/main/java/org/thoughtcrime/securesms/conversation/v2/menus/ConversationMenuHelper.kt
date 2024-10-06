package org.thoughtcrime.securesms.conversation.v2.menus

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.sending_receiving.leave
import org.session.libsession.utilities.GroupUtil.doubleDecodeGroupID
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.GROUP_NAME_KEY
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.guava.Optional
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.ShortcutLauncherActivity
import org.thoughtcrime.securesms.calls.WebRtcCallActivity
import org.thoughtcrime.securesms.contacts.SelectContactsActivity
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.conversation.v2.utilities.NotificationUtils
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.groups.EditClosedGroupActivity
import org.thoughtcrime.securesms.groups.EditClosedGroupActivity.Companion.groupIDKey
import org.thoughtcrime.securesms.media.MediaOverviewActivity
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.preferences.PrivacySettingsActivity
import org.thoughtcrime.securesms.service.WebRtcCallService
import org.thoughtcrime.securesms.showMuteDialog
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.ui.findActivity
import org.thoughtcrime.securesms.ui.getSubbedString
import org.thoughtcrime.securesms.util.BitmapUtil
import java.io.IOException

object ConversationMenuHelper {
    
    fun onPrepareOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
        thread: Recipient,
        context: Context
    ) {
        // Prepare
        menu.clear()
        val isCommunity = thread.isCommunityRecipient
        // Base menu (options that should always be present)
        inflater.inflate(R.menu.menu_conversation, menu)
        // Expiring messages
        if (!isCommunity && (thread.hasApprovedMe() || thread.isClosedGroupRecipient || thread.isLocalNumber)) {
            inflater.inflate(R.menu.menu_conversation_expiration, menu)
        }
        // One-on-one chat menu allows copying the account id
        if (thread.isContactRecipient) {
            inflater.inflate(R.menu.menu_conversation_copy_account_id, menu)
        }
        // One-on-one chat menu (options that should only be present for one-on-one chats)
        if (thread.isContactRecipient) {
            if (thread.isBlocked) {
                inflater.inflate(R.menu.menu_conversation_unblock, menu)
            } else if (!thread.isLocalNumber) {
                inflater.inflate(R.menu.menu_conversation_block, menu)
            }
        }
        // Closed group menu (options that should only be present in closed groups)
        if (thread.isClosedGroupRecipient) {
            inflater.inflate(R.menu.menu_conversation_closed_group, menu)
        }
        // Open group menu
        if (isCommunity) {
            inflater.inflate(R.menu.menu_conversation_open_group, menu)
        }
        // Muting
        if (thread.isMuted) {
            inflater.inflate(R.menu.menu_conversation_muted, menu)
        } else {
            inflater.inflate(R.menu.menu_conversation_unmuted, menu)
        }

        if (thread.isGroupRecipient && !thread.isMuted) {
            inflater.inflate(R.menu.menu_conversation_notification_settings, menu)
        }

        if (thread.showCallMenu()) {
            inflater.inflate(R.menu.menu_conversation_call, menu)
        }

        // Search
        val searchViewItem = menu.findItem(R.id.menu_search)
        (context as ConversationActivityV2).searchViewItem = searchViewItem
        val searchView = searchViewItem.actionView as SearchView
        val queryListener = object : OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return true
            }

            override fun onQueryTextChange(query: String): Boolean {
                context.onSearchQueryUpdated(query)
                return true
            }
        }
        searchViewItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                searchView.setOnQueryTextListener(queryListener)
                context.onSearchOpened()
                for (i in 0 until menu.size()) {
                    if (menu.getItem(i) != searchViewItem) {
                        menu.getItem(i).isVisible = false
                    }
                }
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                searchView.setOnQueryTextListener(null)
                context.onSearchClosed()
                return true
            }
        })
    }

    fun onOptionItemSelected(context: Context, item: MenuItem, thread: Recipient): Boolean {
        when (item.itemId) {
            R.id.menu_view_all_media -> { showAllMedia(context, thread) }
            R.id.menu_search -> { search(context) }
            R.id.menu_add_shortcut -> { addShortcut(context, thread) }
            R.id.menu_expiring_messages -> { showDisappearingMessages(context, thread) }
            R.id.menu_unblock -> { unblock(context, thread) }
            R.id.menu_block -> { block(context, thread, deleteThread = false) }
            R.id.menu_block_delete -> { blockAndDelete(context, thread) }
            R.id.menu_copy_account_id -> { copyAccountID(context, thread) }
            R.id.menu_copy_open_group_url -> { copyOpenGroupUrl(context, thread) }
            R.id.menu_edit_group -> { editClosedGroup(context, thread) }
            R.id.menu_leave_group -> { leaveClosedGroup(context, thread) }
            R.id.menu_invite_to_open_group -> { inviteContacts(context, thread) }
            R.id.menu_unmute_notifications -> { unmute(context, thread) }
            R.id.menu_mute_notifications -> { mute(context, thread) }
            R.id.menu_notification_settings -> { setNotifyType(context, thread) }
            R.id.menu_call -> { call(context, thread) }
        }
        return true
    }

    private fun showAllMedia(context: Context, thread: Recipient) {
        val activity = context as AppCompatActivity
        activity.startActivity(MediaOverviewActivity.createIntent(context, thread.address))
    }

    private fun search(context: Context) {
        val searchViewModel = (context as ConversationActivityV2).searchViewModel
        searchViewModel.onSearchOpened()
    }

    private fun call(context: Context, thread: Recipient) {

        // if the user has not enabled voice/video calls
        if (!TextSecurePreferences.isCallNotificationsEnabled(context)) {
            context.showSessionDialog {
                title(R.string.callsPermissionsRequired)
                text(R.string.callsPermissionsRequiredDescription)
                button(R.string.sessionSettings, R.string.AccessibilityId_sessionSettings) {
                    Intent(context, PrivacySettingsActivity::class.java).let(context::startActivity)
                }
                cancelButton()
            }
            return
        }
        // or if the user has not granted audio/microphone permissions
        else if (!Permissions.hasAll(context, Manifest.permission.RECORD_AUDIO)) {
            Log.d("Loki", "Attempted to make a call without audio permissions")

            Permissions.with(context.findActivity())
                .request(Manifest.permission.RECORD_AUDIO)
                .withPermanentDenialDialog(
                    context.getSubbedString(R.string.permissionsMicrophoneAccessRequired,
                    APP_NAME_KEY to context.getString(R.string.app_name))
                )
                .execute()

            return
        }

        WebRtcCallService.createCall(context, thread)
            .let(context::startService)

        Intent(context, WebRtcCallActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            .let(context::startActivity)
    }

    @SuppressLint("StaticFieldLeak")
    private fun addShortcut(context: Context, thread: Recipient) {
        object : AsyncTask<Void?, Void?, IconCompat?>() {

            @Deprecated("Deprecated in Java")
            override fun doInBackground(vararg params: Void?): IconCompat? {
                var icon: IconCompat? = null
                val contactPhoto = thread.contactPhoto
                if (contactPhoto != null) {
                    try {
                        var bitmap = BitmapFactory.decodeStream(contactPhoto.openInputStream(context))
                        bitmap = BitmapUtil.createScaledBitmap(bitmap, 300, 300)
                        icon = IconCompat.createWithAdaptiveBitmap(bitmap)
                    } catch (e: IOException) {
                        // Do nothing
                    }
                }
                if (icon == null) {
                    icon = IconCompat.createWithResource(context, if (thread.isGroupRecipient) R.mipmap.ic_group_shortcut else R.mipmap.ic_person_shortcut)
                }
                return icon
            }

            @Deprecated("Deprecated in Java")
            override fun onPostExecute(icon: IconCompat?) {
                val name = Optional.fromNullable<String>(thread.name)
                    .or(Optional.fromNullable<String>(thread.profileName))
                    .or(thread.toShortString())
                val shortcutInfo = ShortcutInfoCompat.Builder(context, thread.address.serialize() + '-' + System.currentTimeMillis())
                    .setShortLabel(name)
                    .setIcon(icon)
                    .setIntent(ShortcutLauncherActivity.createIntent(context, thread.address))
                    .build()
                if (ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)) {
                    Toast.makeText(context, context.resources.getString(R.string.conversationsAddedToHome), Toast.LENGTH_LONG).show()
                }
            }
        }.execute()
    }

    private fun showDisappearingMessages(context: Context, thread: Recipient) {
        val listener = context as? ConversationMenuListener ?: return
        listener.showDisappearingMessages(thread)
    }

    private fun unblock(context: Context, thread: Recipient) {
        if (!thread.isContactRecipient) { return }
        val listener = context as? ConversationMenuListener ?: return
        listener.unblock()
    }

    private fun block(context: Context, thread: Recipient, deleteThread: Boolean) {
        if (!thread.isContactRecipient) { return }
        val listener = context as? ConversationMenuListener ?: return
        listener.block(deleteThread)
    }

    private fun blockAndDelete(context: Context, thread: Recipient) {
        if (!thread.isContactRecipient) { return }
        val listener = context as? ConversationMenuListener ?: return
        listener.block(deleteThread = true)
    }

    private fun copyAccountID(context: Context, thread: Recipient) {
        if (!thread.isContactRecipient) { return }
        val listener = context as? ConversationMenuListener ?: return
        listener.copyAccountID(thread.address.toString())
    }

    private fun copyOpenGroupUrl(context: Context, thread: Recipient) {
        if (!thread.isCommunityRecipient) { return }
        val listener = context as? ConversationMenuListener ?: return
        listener.copyOpenGroupUrl(thread)
    }

    private fun editClosedGroup(context: Context, thread: Recipient) {
        if (!thread.isClosedGroupRecipient) { return }
        val intent = Intent(context, EditClosedGroupActivity::class.java)
        val groupID: String = thread.address.toGroupString()
        intent.putExtra(groupIDKey, groupID)
        context.startActivity(intent)
    }

    private fun leaveClosedGroup(context: Context, thread: Recipient) {
        if (!thread.isClosedGroupRecipient) { return }

        val group = DatabaseComponent.get(context).groupDatabase().getGroup(thread.address.toGroupString()).orNull()
        val admins = group.admins
        val accountID = TextSecurePreferences.getLocalNumber(context)
        val isCurrentUserAdmin = admins.any { it.toString() == accountID }
        val message = if (isCurrentUserAdmin) {
            Phrase.from(context, R.string.groupDeleteDescription)
                .put(GROUP_NAME_KEY, group.title)
                .format()
        } else {
            Phrase.from(context, R.string.groupLeaveDescription)
                .put(GROUP_NAME_KEY, group.title)
                .format()
        }

        fun onLeaveFailed() {
            val txt = Phrase.from(context, R.string.groupLeaveErrorFailed)
                .put(GROUP_NAME_KEY, group.title)
                .format().toString()
            Toast.makeText(context, txt, Toast.LENGTH_LONG).show()
        }

        context.showSessionDialog {
            title(R.string.groupLeave)
            text(message)
            dangerButton(R.string.leave) {
                try {
                    val groupPublicKey = doubleDecodeGroupID(thread.address.toString()).toHexString()
                    val isClosedGroup = DatabaseComponent.get(context).lokiAPIDatabase().isClosedGroup(groupPublicKey)

                    if (isClosedGroup) MessageSender.leave(groupPublicKey, notifyUser = false)
                    else onLeaveFailed()
                } catch (e: Exception) {
                    onLeaveFailed()
                }
            }
            button(R.string.cancel)
        }
    }

    private fun inviteContacts(context: Context, thread: Recipient) {
        if (!thread.isCommunityRecipient) { return }
        val intent = Intent(context, SelectContactsActivity::class.java)
        val activity = context as AppCompatActivity
        activity.startActivityForResult(intent, ConversationActivityV2.INVITE_CONTACTS)
    }

    private fun unmute(context: Context, thread: Recipient) {
        DatabaseComponent.get(context).recipientDatabase().setMuted(thread, 0)
    }

    private fun mute(context: Context, thread: Recipient) {
        showMuteDialog(ContextThemeWrapper(context, context.theme)) { until: Long ->
            DatabaseComponent.get(context).recipientDatabase().setMuted(thread, until)
        }
    }

    private fun setNotifyType(context: Context, thread: Recipient) {
        NotificationUtils.showNotifyDialog(context, thread) { notifyType ->
            DatabaseComponent.get(context).recipientDatabase().setNotifyType(thread, notifyType)
        }
    }

    interface ConversationMenuListener {
        fun block(deleteThread: Boolean = false)
        fun unblock()
        fun copyAccountID(accountId: String)
        fun copyOpenGroupUrl(thread: Recipient)
        fun showDisappearingMessages(thread: Recipient)
    }

}