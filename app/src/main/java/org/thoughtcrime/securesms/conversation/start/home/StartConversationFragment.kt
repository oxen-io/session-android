package org.thoughtcrime.securesms.conversation.start.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.conversation.start.StartConversationDelegate
import org.thoughtcrime.securesms.conversation.start.NullStartConversationDelegate
import org.thoughtcrime.securesms.ui.createThemedComposeView
import javax.inject.Inject

@AndroidEntryPoint
class StartConversationHomeFragment : Fragment() {

    @Inject
    lateinit var textSecurePreferences: TextSecurePreferences

    var delegate = MutableStateFlow<StartConversationDelegate>(NullStartConversationDelegate)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = createThemedComposeView {
        StartConversationScreen(
            accountId = TextSecurePreferences.getLocalNumber(requireContext())!!,
            delegate = delegate.collectAsState().value
        )
    }
}
