package org.thoughtcrime.securesms.groups.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import network.loki.messenger.R
import org.thoughtcrime.securesms.groups.ContactItem
import org.thoughtcrime.securesms.groups.CreateGroupEvent
import org.thoughtcrime.securesms.groups.CreateGroupViewModel
import org.thoughtcrime.securesms.ui.CloseIcon
import org.thoughtcrime.securesms.ui.LoadingArcOr
import org.thoughtcrime.securesms.ui.NavigationBar
import org.thoughtcrime.securesms.ui.SearchBar
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineButton
import org.thoughtcrime.securesms.ui.components.SessionOutlinedTextField
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme


@Composable
fun CreateGroupScreen(
    onNavigateToConversationScreen: (groupAccountId: String) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val viewModel: CreateGroupViewModel = hiltViewModel()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CreateGroupEvent.NavigateToConversation -> {
                    onClose()
                    onNavigateToConversationScreen(event.groupAccountId)
                }
            }
        }
    }

    CreateGroup(
        groupName = viewModel.groupName.collectAsState().value,
        onGroupNameChanged = viewModel::onGroupNameChanged,
        groupNameError = viewModel.groupNameError.collectAsState().value,
        contactSearchQuery = viewModel.selectContactsViewModel.searchQuery.collectAsState().value,
        onContactSearchQueryChanged = viewModel.selectContactsViewModel::onSearchQueryChanged,
        onContactItemClicked = viewModel.selectContactsViewModel::onContactItemClicked,
        showLoading = viewModel.isLoading.collectAsState().value,
        items = viewModel.selectContactsViewModel.contacts.collectAsState().value,
        onCreateClicked = viewModel::onCreateClicked,
        onBack = onBack,
        onClose = onClose,
    )
}

@Composable
fun CreateGroup(
    groupName: String,
    onGroupNameChanged: (String) -> Unit,
    groupNameError: String,
    contactSearchQuery: String,
    onContactSearchQueryChanged: (String) -> Unit,
    onContactItemClicked: (accountID: String) -> Unit,
    showLoading: Boolean,
    items: List<ContactItem>,
    onCreateClicked: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        NavigationBar(
            title = stringResource(id = R.string.activity_create_group_title),
            onBack = onBack,
            actionElement = { CloseIcon(onClose) }
        )

        SessionOutlinedTextField(
            text = groupName,
            onChange = onGroupNameChanged,
            placeholder = stringResource(R.string.dialog_edit_group_information_enter_group_name),
            textStyle = LocalType.current.base,
            modifier = Modifier.padding(horizontal = 16.dp),
            error = groupNameError.takeIf { it.isNotBlank() },
            onContinue = {
                focusManager.clearFocus()
            }
        )

        SearchBar(
            query = contactSearchQuery,
            onValueChanged = onContactSearchQueryChanged,
            placeholder = stringResource(R.string.search_contacts_hint),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            multiSelectMemberList(items, onContactItemClicked = onContactItemClicked)
        }

        PrimaryOutlineButton(onClick = onCreateClicked) {
            LoadingArcOr(loading = showLoading) {
                Text(stringResource(R.string.activity_create_group_create_button_title))
            }
        }
    }
}

@Preview
@Composable
private fun CreateGroupPreview(
) {
    val random = "05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234"
    val previewMembers = listOf(
        ContactItem(random, "Alice", false),
        ContactItem(random, "Bob", true),
    )
    PreviewTheme {
        CreateGroup(
            modifier = Modifier.background(LocalColors.current.backgroundSecondary),
            groupName = "Group Name",
            onGroupNameChanged = {},
            contactSearchQuery = "",
            onContactSearchQueryChanged = {},
            onContactItemClicked = {},
            items = previewMembers,
            onBack = {},
            onClose = {},
            onCreateClicked = {},
            showLoading = false,
            groupNameError = "",
        )
    }

}

