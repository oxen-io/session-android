package org.thoughtcrime.securesms.groups.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.serialization.Serializable
import network.loki.messenger.R
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.groups.ContactItem
import org.thoughtcrime.securesms.groups.SelectContactsViewModel
import org.thoughtcrime.securesms.ui.BottomFadingEdgeBox
import org.thoughtcrime.securesms.ui.SearchBar
import org.thoughtcrime.securesms.ui.components.BackAppBar
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineButton
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.PreviewTheme


@Serializable
object RouteSelectContacts

@Composable
fun SelectContactsScreen(
    excludingAccountIDs: Set<AccountId> = emptySet(),
    onDoneClicked: (selectedContacts: Set<AccountId>) -> Unit,
    onBackClicked: () -> Unit,
) {
    val viewModel = hiltViewModel<SelectContactsViewModel, SelectContactsViewModel.Factory> { factory ->
        factory.create(excludingAccountIDs)
    }

    SelectContacts(
        contacts = viewModel.contacts.collectAsState().value,
        onContactItemClicked = viewModel::onContactItemClicked,
        searchQuery = viewModel.searchQuery.collectAsState().value,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onDoneClicked = { onDoneClicked(viewModel.currentSelected) },
        onBack = onBackClicked,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectContacts(
    contacts: List<ContactItem>,
    onContactItemClicked: (accountId: AccountId) -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onDoneClicked: () -> Unit,
    onBack: () -> Unit,
    @StringRes okButtonResId: Int = R.string.ok
) {
    Column(verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallSpacing)) {
        BackAppBar(
            title = stringResource(id = R.string.contactSelect),
            onBack = onBack,
        )

        GroupMinimumVersionBanner()
        SearchBar(
            query = searchQuery,
            onValueChanged = onSearchQueryChanged,
            placeholder = stringResource(R.string.searchContacts),
            modifier = Modifier.padding(horizontal = LocalDimensions.current.smallSpacing),
            backgroundColor = LocalColors.current.backgroundSecondary,
        )

        val scrollState = rememberLazyListState()

        BottomFadingEdgeBox(modifier = Modifier.weight(1f)) { bottomContentPadding ->
            LazyColumn(
                state = scrollState,
                contentPadding = PaddingValues(bottom = bottomContentPadding),
            ) {
                multiSelectMemberList(
                    contacts = contacts,
                    onContactItemClicked = onContactItemClicked,
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            PrimaryOutlineButton(
                onClick = onDoneClicked,
                modifier = Modifier
                    .padding(vertical = LocalDimensions.current.spacing)
                    .defaultMinSize(minWidth = LocalDimensions.current.minButtonWidth),
            ) {
                Text(
                    stringResource(id = okButtonResId)
                )
            }
        }
    }

}

@Preview
@Composable
private fun PreviewSelectContacts() {
    val random = "05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234"
    val contacts = List(20) {
        ContactItem(
            accountID = AccountId(random),
            name = "User $it",
            selected = it % 3 == 0,
        )
    }

    PreviewTheme {
        SelectContacts(
            contacts = contacts,
            onContactItemClicked = {},
            searchQuery = "",
            onSearchQueryChanged = {},
            onDoneClicked = {},
            onBack = {},
        )
    }
}

