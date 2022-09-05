package org.thoughtcrime.securesms.home

import android.content.Intent
import android.os.Bundle
import network.loki.messenger.databinding.ActivityNewConversationBinding
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.dms.CreatePrivateChatActivity
import org.thoughtcrime.securesms.groups.CreateClosedGroupActivity
import org.thoughtcrime.securesms.groups.JoinPublicChatActivity
import org.thoughtcrime.securesms.util.show

class NewConversationActivity : PassphraseRequiredActionBarActivity() {

    private lateinit var binding: ActivityNewConversationBinding

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        binding = ActivityNewConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.newMessageButton.setOnClickListener { createNewPrivateChat() }
        binding.newGroupButton.setOnClickListener { createNewClosedGroup() }
        binding.joinCommunityButton.setOnClickListener { joinCommunity() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == CreateClosedGroupActivity.closedGroupCreatedResultCode) {
            createNewPrivateChat()
        }
    }

    fun createNewPrivateChat() {
        val intent = Intent(this, CreatePrivateChatActivity::class.java)
        show(intent)
    }

    fun createNewClosedGroup() {
        val intent = Intent(this, CreateClosedGroupActivity::class.java)
        show(intent, true)
    }

    fun joinCommunity() {
        val intent = Intent(this, JoinPublicChatActivity::class.java)
        show(intent)
    }
}