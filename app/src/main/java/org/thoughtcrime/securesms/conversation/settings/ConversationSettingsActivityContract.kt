package org.thoughtcrime.securesms.conversation.settings

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2

sealed class ConversationSettingsActivityResult {
    object Finished: ConversationSettingsActivityResult()
    object SearchConversation: ConversationSettingsActivityResult()
}

class ConversationSettingsActivityContract: ActivityResultContract<Long, ConversationSettingsActivityResult>() {

    override fun createIntent(context: Context, input: Long) = Intent(context, ConversationSettingsActivity::class.java).apply {
        putExtra(ConversationActivityV2.THREAD_ID, input ?: -1L)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): ConversationSettingsActivityResult =
        when (resultCode) {
            ConversationSettingsActivity.RESULT_SEARCH -> ConversationSettingsActivityResult.SearchConversation
            else -> ConversationSettingsActivityResult.Finished
        }
}