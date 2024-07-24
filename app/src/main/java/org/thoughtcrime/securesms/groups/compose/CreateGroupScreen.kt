package org.thoughtcrime.securesms.groups.compose

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import org.thoughtcrime.securesms.conversation.start.StartConversationDelegate
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.groups.ContactList
import org.thoughtcrime.securesms.groups.CreateGroupViewModel
import org.thoughtcrime.securesms.groups.StateUpdate
import org.thoughtcrime.securesms.groups.ViewState

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