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
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush.Companion.verticalGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.thoughtcrime.securesms.home.search.getSearchName
import org.thoughtcrime.securesms.ui.CloseIcon
import org.thoughtcrime.securesms.ui.NavigationBar
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SearchBar
import org.thoughtcrime.securesms.ui.ThemeResPreviewParameterProvider

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectContacts(
    contactListState: Set<Contact>,
    onBack: ()->Unit,
    onClose: (()->Unit)? = null,
    onContactsSelected: (Set<Contact>) -> Unit,
    @StringRes okButtonResId: Int = R.string.ok
) {

    var queryFilter by remember { mutableStateOf("") }

    // May introduce more advanced filters
    val filtered = if (queryFilter.isEmpty()) contactListState.toList()
        else {
            contactListState
            .filter { contact ->
                contact.getSearchName().lowercase()
                    .contains(queryFilter.lowercase())
            }
                .toList()
        }

    var selected by remember {
        mutableStateOf(emptySet<Contact>())
    }

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
                SearchBar(queryFilter, onValueChanged = { value -> queryFilter = value })
            }

            multiSelectMemberList(
                contacts = filtered.toList(),
                selectedContacts = selected,
                onListUpdated = { selected = it },
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth()
                .background(
                    verticalGradient(
                        0f to Color.Transparent,
                        0.2f to MaterialTheme.colors.primaryVariant,
                    )
                )
        ) {
            OutlinedButton(
                onClick = { onContactsSelected(selected) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).defaultMinSize(minWidth = 128.dp),
                border = BorderStroke(1.dp, MaterialTheme.colors.onPrimary),
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = Color.Transparent,
                    contentColor = MaterialTheme.colors.onPrimary,
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
fun previewSelectContacts(
    @PreviewParameter(ThemeResPreviewParameterProvider::class) themeRes: Int
) {
    val empty = emptySet<Contact>()
    PreviewTheme(themeResId = themeRes) {
        SelectContacts(contactListState = empty, onBack = { /*TODO*/ }, onContactsSelected = {})
    }
}

