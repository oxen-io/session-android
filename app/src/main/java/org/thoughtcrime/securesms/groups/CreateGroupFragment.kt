package org.thoughtcrime.securesms.groups

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.thoughtcrime.securesms.conversation.start.NullStartConversationDelegate
import org.thoughtcrime.securesms.conversation.start.StartConversationDelegate
import org.thoughtcrime.securesms.groups.compose.CreateGroupScreen
import org.thoughtcrime.securesms.ui.theme.SessionMaterialTheme

@AndroidEntryPoint
class CreateGroupFragment : Fragment() {
    private val viewModel: CreateGroupViewModel by viewModels()

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
                        viewModel = viewModel,
                        onFinishCreation = delegate::onDialogClosePressed,
                        onCancelCreation = delegate::onDialogBackPressed
                    )
                }
            }
        }
    }
}

