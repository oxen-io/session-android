package org.thoughtcrime.securesms.groups.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush.Companion.verticalGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.serialization.Serializable
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.thoughtcrime.securesms.groups.ContactItem
import org.thoughtcrime.securesms.groups.SelectContactsViewModel
import org.thoughtcrime.securesms.ui.SearchBar
import org.thoughtcrime.securesms.ui.components.BackAppBar
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineButton
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.PreviewTheme


@Serializable
object RouteSelectContacts

@Composable
fun SelectContactsScreen(
    excludingAccountIDs: Set<String> = emptySet(),
    onDoneClicked: (selectedContacts: Set<Contact>) -> Unit,
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
    onContactItemClicked: (accountId: String) -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onDoneClicked: () -> Unit,
    onBack: () -> Unit,
    @StringRes okButtonResId: Int = R.string.ok
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BackAppBar(
            title = stringResource(id = R.string.contactSelect),
            onBack = onBack,
        )

        GroupMinimumVersionBanner()
        SearchBar(
            query = searchQuery,
            onValueChanged = onSearchQueryChanged,
            placeholder = stringResource(R.string.searchContacts),
            modifier = Modifier.padding(horizontal = 16.dp),
            backgroundColor = LocalColors.current.backgroundSecondary,
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            multiSelectMemberList(
                contacts = contacts,
                onContactItemClicked = onContactItemClicked,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    verticalGradient(
                        0f to Color.Transparent,
                        0.2f to LocalColors.current.background,
                    )
                )
        ) {
            PrimaryOutlineButton(
                onClick = onDoneClicked,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .defaultMinSize(minWidth = 128.dp),
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
    PreviewTheme {
        SelectContacts(
            contacts = listOf(
                ContactItem(
                    contact = Contact(accountID = "123", name = "User 1"),
                    selected = false,
                ),
                ContactItem(
                    contact = Contact(accountID = "124", name = "User 2"),
                    selected = true,
                ),
            ),
            onContactItemClicked = {},
            searchQuery = "",
            onSearchQueryChanged = {},
            onDoneClicked = {},
            onBack = {},
        )
    }
}

