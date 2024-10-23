package org.thoughtcrime.securesms.groups.compose

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import network.loki.messenger.R
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.groups.ContactItem
import org.thoughtcrime.securesms.groups.CreateGroupEvent
import org.thoughtcrime.securesms.groups.CreateGroupViewModel
import org.thoughtcrime.securesms.ui.LoadingArcOr
import org.thoughtcrime.securesms.ui.SearchBar
import org.thoughtcrime.securesms.ui.components.BackAppBar
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineButton
import org.thoughtcrime.securesms.ui.components.SessionOutlinedTextField
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme


@Composable
fun CreateGroupScreen(
    onNavigateToConversationScreen: (threadID: Long) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val viewModel: CreateGroupViewModel = hiltViewModel()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CreateGroupEvent.NavigateToConversation -> {
                    onClose()
                    onNavigateToConversationScreen(event.threadID)
                }

                is CreateGroupEvent.Error -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroup(
    groupName: String,
    onGroupNameChanged: (String) -> Unit,
    groupNameError: String,
    contactSearchQuery: String,
    onContactSearchQueryChanged: (String) -> Unit,
    onContactItemClicked: (accountID: AccountId) -> Unit,
    showLoading: Boolean,
    items: List<ContactItem>,
    onCreateClicked: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier.padding(bottom = LocalDimensions.current.mediumSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BackAppBar(
            title = stringResource(id = R.string.groupCreate),
            onBack = onBack,
        )

        SessionOutlinedTextField(
            text = groupName,
            onChange = onGroupNameChanged,
            placeholder = stringResource(R.string.groupNameEnter),
            textStyle = LocalType.current.base,
            modifier = Modifier.padding(horizontal = 16.dp),
            error = groupNameError.takeIf { it.isNotBlank() },
            enabled = !showLoading,
            onContinue = focusManager::clearFocus
        )

        SearchBar(
            query = contactSearchQuery,
            onValueChanged = onContactSearchQueryChanged,
            placeholder = stringResource(R.string.searchContacts),
            modifier = Modifier.padding(horizontal = 16.dp),
            enabled = !showLoading
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            multiSelectMemberList(
                contacts = items,
                onContactItemClicked = onContactItemClicked,
                enabled = !showLoading
            )
        }

        PrimaryOutlineButton(onClick = onCreateClicked, modifier = Modifier.widthIn(min = 120.dp)) {
            LoadingArcOr(loading = showLoading) {
                Text(stringResource(R.string.create))
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
        ContactItem(accountID = AccountId(random), name = "Alice", false),
        ContactItem(accountID = AccountId(random), name = "Bob", true),
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

