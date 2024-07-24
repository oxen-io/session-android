package org.thoughtcrime.securesms.groups.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import org.thoughtcrime.securesms.conversation.start.StartConversationDelegate
import org.thoughtcrime.securesms.groups.ContactList
import org.thoughtcrime.securesms.groups.CreateGroupViewModel
import org.thoughtcrime.securesms.groups.ViewState

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