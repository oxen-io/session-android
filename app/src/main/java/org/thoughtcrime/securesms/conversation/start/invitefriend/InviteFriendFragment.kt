package org.thoughtcrime.securesms.conversation.start.invitefriend

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.conversation.start.StartConversationDelegate
import org.thoughtcrime.securesms.preferences.copyPublicKey
import org.thoughtcrime.securesms.preferences.sendInvitationToUseSession
import org.thoughtcrime.securesms.ui.createThemedComposeView

@AndroidEntryPoint
class InviteFriendFragment : Fragment() {
    lateinit var delegate: StartConversationDelegate

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = createThemedComposeView {
        InviteFriend(
            TextSecurePreferences.getLocalNumber(LocalContext.current)!!,
            onBack = { delegate.onDialogBackPressed() },
            onClose = { delegate.onDialogClosePressed() },
            copyPublicKey = requireContext()::copyPublicKey,
            sendInvitation = requireContext()::sendInvitationToUseSession,
        )
    }
}
