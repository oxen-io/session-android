package org.thoughtcrime.securesms.groups

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.conversation.start.NullStartConversationDelegate
import org.thoughtcrime.securesms.conversation.start.StartConversationDelegate
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.groups.compose.CreateGroupScreen
import org.thoughtcrime.securesms.ui.theme.SessionMaterialTheme

class CreateGroupFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            val delegate = (parentFragment as? StartConversationDelegate)
                ?: (activity as? StartConversationDelegate)
                ?: NullStartConversationDelegate

            setContent {
                SessionMaterialTheme {
                    CreateGroupScreen(
                        onNavigateToConversationScreen = { threadID ->
                            startActivity(
                                Intent(requireContext(), ConversationActivityV2::class.java)
                                    .putExtra(ConversationActivityV2.THREAD_ID, threadID)
                            )
                        },
                        onBack = delegate::onDialogBackPressed,
                        onClose = delegate::onDialogClosePressed
                    )
                }
            }
        }
    }
}

