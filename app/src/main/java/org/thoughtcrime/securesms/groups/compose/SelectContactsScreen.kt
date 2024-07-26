package org.thoughtcrime.securesms.groups.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import org.thoughtcrime.securesms.ui.CloseIcon
import org.thoughtcrime.securesms.ui.NavigationBar
import org.thoughtcrime.securesms.ui.SearchBar
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.PreviewTheme


@Serializable
data class RouteSelectContacts(
    /**
     * If set, only select contacts from these account IDs.
     * If null, select from all contacts.
     */
    val onlySelectFromAccountIDs: List<String>? = null
)

@Composable
fun SelectContactsScreen(
    onlySelectFromAccountIDs: Set<String>?,
    onDoneClicked: (selectedContacts: Set<Contact>) -> Unit,
    onBackClicked: () -> Unit,
) {
    val viewModel = hiltViewModel<SelectContactsViewModel, SelectContactsViewModel.Factory> { factory ->
        factory.create(onlySelectFromAccountIDs)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectContacts(
    contacts: List<ContactItem>,
    onContactItemClicked: (accountId: String) -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onDoneClicked: () -> Unit,
    onBack: () -> Unit,
    onClose: (() -> Unit)? = null,
    @StringRes okButtonResId: Int = R.string.ok
) {
    Column {
        NavigationBar(
            title = stringResource(id = R.string.activity_create_closed_group_select_contacts),
            onBack = onBack,
            actionElement = {
                if (onClose != null) {
                    CloseIcon(onClose)
                }
            }
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            stickyHeader {
                GroupMinimumVersionBanner()
                // Search Bar
                SearchBar(query = searchQuery, onValueChanged = onSearchQueryChanged)
            }

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
                        0.2f to LocalColors.current.primary,
                    )
                )
        ) {
            OutlinedButton(
                onClick = onDoneClicked,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .defaultMinSize(minWidth = 128.dp),
                border = BorderStroke(1.dp, LocalColors.current.borders),
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = LocalColors.current.text,
                )
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
fun PreviewSelectContacts() {
    PreviewTheme {
        SelectContacts(
            contacts = emptyList(),
            onContactItemClicked = {},
            searchQuery = "",
            onSearchQueryChanged = {},
            onDoneClicked = {},
            onBack = {},
            onClose = null
        )
    }
}

