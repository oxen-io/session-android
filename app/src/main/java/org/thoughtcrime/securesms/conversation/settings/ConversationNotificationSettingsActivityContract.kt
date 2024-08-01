package org.thoughtcrime.securesms.conversation.settings

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2

class ConversationNotificationSettingsActivityContract: ActivityResultContract<Long, Unit>() {

    override fun createIntent(context: Context, input: Long): Intent =
        Intent(context, ConversationNotificationSettingsActivity::class.java).apply {
            putExtra(ConversationActivityV2.THREAD_ID, input)
        }

    override fun parseResult(resultCode: Int, intent: Intent?) { /* do nothing */ }
}