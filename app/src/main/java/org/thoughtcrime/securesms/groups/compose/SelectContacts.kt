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
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.thoughtcrime.securesms.home.search.getSearchName
import org.thoughtcrime.securesms.ui.CloseIcon
import org.thoughtcrime.securesms.ui.NavigationBar
import org.thoughtcrime.securesms.ui.SearchBar
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.PreviewTheme

@Serializable
object RouteSelectContacts

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectContacts(
    selectFrom: List<Contact>,
    onBack: ()->Unit,
    onClose: (()->Unit)? = null,
    onContactsSelected: (List<Contact>) -> Unit,
    @StringRes okButtonResId: Int = R.string.ok
) {

    var queryFilter by remember { mutableStateOf("") }

    // May introduce more advanced filters
    val filtered = if (queryFilter.isEmpty()) selectFrom
        else {
            selectFrom
            .filter { contact ->
                contact.getSearchName().lowercase()
                    .contains(queryFilter.lowercase())
            }
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
                        0.2f to LocalColors.current.primary,
                    )
                )
        ) {
            OutlinedButton(
                onClick = { onContactsSelected(selected.toList()) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).defaultMinSize(minWidth = 128.dp),
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
    PreviewTheme() {
        SelectContacts(selectFrom = emptyList(), onBack = {  }, onContactsSelected = {})
    }
}

