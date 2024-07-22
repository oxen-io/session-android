package org.thoughtcrime.securesms.groups

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.dependency
import com.ramcosta.composedestinations.result.NavResult
import com.ramcosta.composedestinations.result.ResultBackNavigator
import com.ramcosta.composedestinations.result.ResultRecipient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.parcelize.Parcelize
import network.loki.messenger.databinding.FragmentCreateGroupBinding
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.Device
import org.thoughtcrime.securesms.conversation.start.StartConversationDelegate
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.groups.compose.CreateGroup
import org.thoughtcrime.securesms.groups.compose.CreateGroupNavGraph
import org.thoughtcrime.securesms.groups.compose.SelectContacts
import org.thoughtcrime.securesms.groups.compose.StateUpdate
import org.thoughtcrime.securesms.groups.compose.ViewState
import org.thoughtcrime.securesms.groups.destinations.SelectContactsScreenDestination
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

@CreateGroupNavGraph(start = true)
@Composable
@Destination
fun CreateGroupScreen(
    navigator: DestinationsNavigator,
    resultSelectContact: ResultRecipient<SelectContactsScreenDestination, ContactList>,
    viewModel: CreateGroupViewModel = hiltViewModel(),
    getDelegate: () -> StartConversationDelegate
) {
    val viewState by viewModel.viewState.observeAsState(ViewState.DEFAULT)

    resultSelectContact.onNavResult { navResult ->
        when (navResult) {
            is NavResult.Value -> {
                viewModel.updateState(StateUpdate.AddContacts(navResult.value.contacts))
            }

            is NavResult.Canceled -> {
                /* do nothing */
            }
        }
    }

    val context = LocalContext.current

    viewState.createdGroup?.let { group ->
        SideEffect {
            getDelegate().onDialogClosePressed()
            val intent = Intent(context, ConversationActivityV2::class.java).apply {
                putExtra(ConversationActivityV2.ADDRESS, group.address)
            }
            context.startActivity(intent)
        }
    }

    CreateGroup(
        viewState,
        viewModel::updateState,
        onClose = {
            getDelegate().onDialogClosePressed()
        },
        onSelectContact = { navigator.navigate(SelectContactsScreenDestination) },
        onBack = {
            getDelegate().onDialogBackPressed()
        }
    )
}

@CreateGroupNavGraph
@Composable
@Destination
fun SelectContactsScreen(
    resultNavigator: ResultBackNavigator<ContactList>,
    viewModel: CreateGroupViewModel = hiltViewModel(),
    getDelegate: () -> StartConversationDelegate
) {

    val viewState by viewModel.viewState.observeAsState(ViewState.DEFAULT)
    val currentMembers = viewState.members
    val contacts by viewModel.contacts.observeAsState(initial = emptySet())

    SelectContacts(
        contacts - currentMembers,
        onBack = { resultNavigator.navigateBack() },
        onClose = { getDelegate().onDialogClosePressed() },
        onContactsSelected = {
            resultNavigator.navigateBack(ContactList(it))
        }
    )
}