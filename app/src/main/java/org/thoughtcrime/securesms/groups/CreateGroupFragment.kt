package org.thoughtcrime.securesms.groups

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.navigation.dependency
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.parcelize.Parcelize
import network.loki.messenger.databinding.FragmentCreateGroupBinding
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.Device
import org.thoughtcrime.securesms.conversation.start.StartConversationDelegate
import org.thoughtcrime.securesms.ui.theme.SessionMaterialTheme
import javax.inject.Inject

@AndroidEntryPoint
class CreateGroupFragment : Fragment() {

    @Inject
    lateinit var device: Device

    private lateinit var binding: FragmentCreateGroupBinding
    private val viewModel: CreateGroupViewModel by viewModels()

    lateinit var delegate: StartConversationDelegate

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            val getDelegate = { delegate }
            setContent {
                SessionMaterialTheme {
                    DestinationsNavHost(
                        navGraph = NavGraphs.createGroup,
                        dependenciesContainerBuilder = {
                            dependency(getDelegate)
                        })
                }
            }
        }
    }
}

@Parcelize
data class ContactList(val contacts: Set<Contact>) : Parcelable

