package org.thoughtcrime.securesms.conversation.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityConversationSettingsBinding
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.MediaOverviewActivity
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.LokiThreadDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.groups.EditClosedGroupActivity
import org.thoughtcrime.securesms.groups.EditLegacyClosedGroupActivity
import org.thoughtcrime.securesms.showSessionDialog
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class ConversationSettingsActivity: PassphraseRequiredActionBarActivity(), View.OnClickListener {

    companion object {
        // used to trigger displaying conversation search in calling parent activity
        const val RESULT_SEARCH = 22
    }

    lateinit var binding: ActivityConversationSettingsBinding

    private val groupOptions: List<View>
    get() = with(binding) {
        listOf(
            groupMembers,
            groupMembersDivider.root,
            editGroup,
            editGroupDivider.root,
            leaveGroup,
            leaveGroupDivider.root
        )
    }

    @Inject lateinit var threadDb: ThreadDatabase
    @Inject lateinit var groupDb: GroupDatabase
    @Inject lateinit var lokiThreadDb: LokiThreadDatabase
    @Inject lateinit var viewModelFactory: ConversationSettingsViewModel.AssistedFactory
    val viewModel: ConversationSettingsViewModel by viewModels {
        val threadId = intent.getLongExtra(ConversationActivityV2.THREAD_ID, -1L)
        if (threadId == -1L) {
            finish()
        }
        viewModelFactory.create(threadId)
    }

    private val notificationActivityCallback = registerForActivityResult(ConversationNotificationSettingsActivityContract()) {
        updateRecipientDisplay()
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        binding = ActivityConversationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        updateRecipientDisplay()
        binding.searchConversation.setOnClickListener(this)
        binding.clearMessages.setOnClickListener(this)
        binding.allMedia.setOnClickListener(this)
        binding.pinConversation.setOnClickListener(this)
        binding.notificationSettings.setOnClickListener(this)
        binding.editGroup.setOnClickListener(this)
        binding.leaveGroup.setOnClickListener(this)
        binding.back.setOnClickListener(this)
        binding.autoDownloadMediaSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutoDownloadAttachments(isChecked)
            updateRecipientDisplay()
        }
    }

    private fun updateRecipientDisplay() {
        val recipient = viewModel.recipient ?: return
        // Setup profile image
        binding.profilePictureView.root.update(recipient)
        // Setup name
        binding.conversationName.text = when {
            recipient.isLocalNumber -> getString(R.string.note_to_self)
            else -> recipient.toShortString()
        }
        // Setup group description (if group)
        binding.conversationSubtitle.isVisible = recipient.isClosedGroupV2Recipient.apply {
            binding.conversationSubtitle.text = viewModel.closedGroupInfo()?.description
        }

        // Toggle group-specific settings
        val areGroupOptionsVisible = recipient.isClosedGroupV2Recipient || recipient.isLegacyClosedGroupRecipient
        groupOptions.forEach { v ->
            v.isVisible = areGroupOptionsVisible
        }

        // Group admin settings
        val isUserGroupAdmin = areGroupOptionsVisible && viewModel.isUserGroupAdmin()
        with (binding) {
            groupMembersDivider.root.isVisible = areGroupOptionsVisible && !isUserGroupAdmin
            groupMembers.isVisible = !isUserGroupAdmin
            adminControlsGroup.isVisible = isUserGroupAdmin
            deleteGroup.isVisible = isUserGroupAdmin
            clearMessages.isVisible = isUserGroupAdmin
            clearMessagesDivider.root.isVisible = isUserGroupAdmin
            leaveGroupDivider.root.isVisible = isUserGroupAdmin
        }

        // Set pinned state
        binding.pinConversation.setText(
            if (viewModel.isPinned()) R.string.conversation_settings_unpin_conversation
            else R.string.conversation_settings_pin_conversation
        )

        // Set auto-download state
        val trusted = viewModel.autoDownloadAttachments()
        binding.autoDownloadMediaSwitch.isChecked = trusted

        // Set notification type
        val notifyTypes = resources.getStringArray(R.array.notify_types)
        val summary = notifyTypes.getOrNull(recipient.notifyType)
        binding.notificationsValue.text = summary
    }

    override fun onClick(v: View?) {
        val threadRecipient = viewModel.recipient ?: return
        when {
            v === binding.searchConversation -> {
                setResult(RESULT_SEARCH)
                finish()
            }
            v === binding.allMedia -> {
                val intent = Intent(this, MediaOverviewActivity::class.java).apply {
                    putExtra(MediaOverviewActivity.ADDRESS_EXTRA, threadRecipient.address)
                }
                startActivity(intent)
            }
            v === binding.pinConversation -> {
                viewModel.togglePin().invokeOnCompletion { e ->
                    if (e != null) {
                        // something happened
                        Log.e("ConversationSettings", "Failed to toggle pin on thread", e)
                    } else {
                        updateRecipientDisplay()
                    }
                }
            }
            v === binding.notificationSettings -> {
                notificationActivityCallback.launch(viewModel.threadId)
            }
            v === binding.back -> onBackPressed()
            v === binding.clearMessages -> {

                showSessionDialog {
                    title(R.string.dialog_clear_all_messages_title)
                    text(R.string.dialog_clear_all_messages_message)
                    destructiveButton(
                        R.string.dialog_clear_all_messages_clear,
                        R.string.dialog_clear_all_messages_clear) {
                        viewModel.clearMessages(false)
                    }
                    cancelButton()
                }
            }
            v === binding.leaveGroup -> {

                if (threadRecipient.isLegacyClosedGroupRecipient) {
                    // Send a leave group message if this is an active closed group
                    val groupString = threadRecipient.address.toGroupString()
                    val ourId = TextSecurePreferences.getLocalNumber(this)!!
                    if (groupDb.isActive(groupString)) {
                        showSessionDialog {

                            title(R.string.conversation_settings_leave_group)

                            val name = viewModel.recipient!!.name!!
                            val textWithArgs = if (groupDb.getGroup(groupString).get().admins.map(Address::serialize).contains(ourId)) {
                                context.getString(R.string.conversation_settings_leave_group_as_admin)
                            } else {
                                Phrase.from(context, R.string.conversation_settings_leave_group_name)
                                    .put("group", name)
                                    .format()
                            }
                            text(textWithArgs)
                            destructiveButton(
                                R.string.conversation_settings_leave_group,
                                R.string.conversation_settings_leave_group
                            ) {
                                lifecycleScope.launch {
                                    GroupUtil.doubleDecodeGroupID(threadRecipient.address.toString())
                                        .toHexString()
                                        .let { MessageSender.explicitLeave(it, true, deleteThread = true) }
                                    finish()
                                }
                            }
                            cancelButton()
                        }
                        try {

                        } catch (e: IOException) {
                            Log.e("Loki", e)
                        }
                    }
                } else if (threadRecipient.isClosedGroupV2Recipient) {
                    val groupInfo = viewModel.closedGroupInfo()
                    showSessionDialog {

                        title(R.string.conversation_settings_leave_group)

                        val name = viewModel.recipient!!.name!!
                        val textWithArgs = if (groupInfo?.isUserAdmin == true) {
                            context.getString(R.string.conversation_settings_leave_group_as_admin)
                        } else {
                            Phrase.from(context, R.string.conversation_settings_leave_group_name)
                                .put("group", name)
                                .format()
                        }
                        text(textWithArgs)
                        destructiveButton(
                            R.string.conversation_settings_leave_group,
                            R.string.conversation_settings_leave_group
                        ) {
                            lifecycleScope.launch {
                                viewModel.leaveGroup()
                                finish()
                            }
                        }
                        cancelButton()
                    }
                }
            }
            v === binding.editGroup -> {
                val recipient = viewModel.recipient ?: return

                val intent = when {
                    recipient.isLegacyClosedGroupRecipient -> Intent(this, EditLegacyClosedGroupActivity::class.java).apply {
                        val groupID: String = recipient.address.toGroupString()
                        putExtra(EditLegacyClosedGroupActivity.groupIDKey, groupID)
                    }
                    recipient.isClosedGroupV2Recipient -> Intent(this, EditClosedGroupActivity::class.java).apply {
                        val groupID = recipient.address.serialize()
                        putExtra(EditClosedGroupActivity.groupIDKey, groupID)
                    }

                    else -> return
                }
                startActivity(intent)
            }
        }
    }
}