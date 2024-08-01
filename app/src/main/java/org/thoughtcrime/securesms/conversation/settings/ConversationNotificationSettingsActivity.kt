package org.thoughtcrime.securesms.conversation.settings

import android.os.Bundle
import android.view.View
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.databinding.ActivityConversationNotificationSettingsBinding
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import javax.inject.Inject

@AndroidEntryPoint
class ConversationNotificationSettingsActivity: PassphraseRequiredActionBarActivity(), View.OnClickListener {

    lateinit var binding: ActivityConversationNotificationSettingsBinding
    @Inject lateinit var threadDb: ThreadDatabase
    @Inject lateinit var recipientDb: RecipientDatabase
    val recipient by lazy {
        if (threadId == -1L) null
        else threadDb.getRecipientForThreadId(threadId)
    }
    var threadId: Long = -1

    override fun onClick(v: View?) {
        val recipient = recipient ?: return
        if (v === binding.notifyAll) {
            // set notify type
            recipientDb.setNotifyType(recipient, RecipientDatabase.NOTIFY_TYPE_ALL)
        } else if (v === binding.notifyMentions) {
            recipientDb.setNotifyType(recipient, RecipientDatabase.NOTIFY_TYPE_MENTIONS)
        } else if (v === binding.notifyMute) {
            recipientDb.setNotifyType(recipient, RecipientDatabase.NOTIFY_TYPE_NONE)
        }
        updateValues()
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        binding = ActivityConversationNotificationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        threadId = intent.getLongExtra(ConversationActivityV2.THREAD_ID, -1L)
        if (threadId == -1L) finish()
        updateValues()
        with (binding) {
            notifyAll.setOnClickListener(this@ConversationNotificationSettingsActivity)
            notifyMentions.setOnClickListener(this@ConversationNotificationSettingsActivity)
            notifyMute.setOnClickListener(this@ConversationNotificationSettingsActivity)
        }
    }

    private fun updateValues() {
        val notifyType = recipient?.notifyType ?: return
        binding.notifyAllButton.isSelected = notifyType == RecipientDatabase.NOTIFY_TYPE_ALL
        binding.notifyMentionsButton.isSelected = notifyType == RecipientDatabase.NOTIFY_TYPE_MENTIONS
        binding.notifyMuteButton.isSelected = notifyType == RecipientDatabase.NOTIFY_TYPE_NONE
    }

}