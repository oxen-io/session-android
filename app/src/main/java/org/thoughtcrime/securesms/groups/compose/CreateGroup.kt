package org.thoughtcrime.securesms.groups.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.ui.CellWithPaddingAndMargin
import org.thoughtcrime.securesms.ui.CloseIcon
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.NavigationBar
import org.thoughtcrime.securesms.ui.PreviewTheme


@Composable
fun CreateGroup(
    viewState: ViewState,
    updateState: (StateUpdate) -> Unit,
    onSelectContact: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {

    val lazyState = rememberLazyListState()

    Box {
        Column(
            modifier
                .fillMaxWidth()) {
            LazyColumn(state = lazyState) {
                // Top bar
                item {
                    Column(modifier.fillMaxWidth()) {
                        NavigationBar(
                            title = stringResource(id = R.string.activity_create_group_title),
                            onBack = onBack,
                            actionElement = { CloseIcon(onClose) }
                        )
                        // Editable avatar (future chunk)
                        // EditableAvatar(
                        //     modifier = Modifier
                        //         .align(Alignment.CenterHorizontally)
                        //         .padding(top = 16.dp)
                        // )

                        // Title
                        val nameDescription = stringResource(id = R.string.AccessibilityId_closed_group_edit_group_name)
                        OutlinedTextField(
                            value = viewState.name,
                            onValueChange = { updateState(StateUpdate.Name(it)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 8.dp, horizontal = 24.dp)
                                .semantics {
                                    contentDescription = nameDescription
                                },
                        )

                        // Description
                        // val descriptionDescription = stringResource(id = R.string.AccessibilityId_closed_group_edit_group_description)
                        // OutlinedTextField(
                        //     value = viewState.description,
                        //     onValueChange = { updateState(StateUpdate.Description(it)) },
                        //     modifier = Modifier
                        //         .fillMaxWidth()
                        //         .align(Alignment.CenterHorizontally)
                        //         .padding(vertical = 8.dp, horizontal = 24.dp)
                        //         .semantics {
                        //             contentDescription = descriptionDescription
                        //         },
                        // )

                        // Add contacts area
                        CellWithPaddingAndMargin(padding = 0.dp) {
                            Column(Modifier.fillMaxSize()) {
                                // Select Contacts
                                val padding = Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth()
                                Row(padding.clickable {
                                    onSelectContact()
                                }) {
                                    Image(
                                        painterResource(id = R.drawable.ic_person_white_24dp),
                                        null,
                                        Modifier
                                            .padding(4.dp)
                                            .align(Alignment.CenterVertically)
                                    )
                                    Text(
                                        stringResource(id = R.string.activity_create_closed_group_select_contacts),
                                        Modifier
                                            .padding(4.dp)
                                            .align(Alignment.CenterVertically)
                                    )
                                }
                                Divider()
                                // Add account ID or ONS
                                // Row(padding) {
                                //     Image(
                                //         painterResource(id = R.drawable.ic_baseline_add_24),
                                //         null,
                                //         Modifier
                                //             .padding(4.dp)
                                //             .align(Alignment.CenterVertically)
                                //     )
                                //     Text(
                                //         stringResource(id = R.string.activity_create_closed_group_add_account_or_ons),
                                //         Modifier
                                //             .padding(4.dp)
                                //             .align(Alignment.CenterVertically)
                                //     )
                                // }
                            }
                        }
                    }
                }
                // Group list
                deleteMemberList(contacts = viewState.members, modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp)) { deletedContact ->
                    updateState(StateUpdate.RemoveContact(deletedContact))
                }
            }
            // Create button
            val createDescription = stringResource(id = R.string.AccessibilityId_create_closed_group_create_button)
            OutlinedButton(
                onClick = { updateState(StateUpdate.Create) },
                enabled = viewState.canCreate,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp)
                    .semantics {
                        contentDescription = createDescription
                    }
                ,
                shape = RoundedCornerShape(32.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.activity_create_group_create_button_title),
                    // TODO: colours of everything here probably needs to be redone
                    color = MaterialTheme.colors.onBackground,
                    modifier = Modifier.width(160.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        if (viewState.isLoading) {
            Log.d("Compose", "Should show loading state")
            Box(modifier = modifier
                .fillMaxSize()
                .background(Color.Gray.copy(alpha = 0.5f))) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colors.secondary
                )
            }
        }
    }
}

@Preview
@Composable
fun ClosedGroupPreview(
) {
    val random = "05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234"
    val previewMembers = setOf(
        Contact(random).apply {
            name = "Person"
        }
    )
    PreviewTheme(R.style.Base_Theme_Session_ForceDark) {
        CreateGroup(
            viewState = ViewState.DEFAULT.copy(
                // override any preview parameters
                members = previewMembers.toList()
            ),
            updateState = {},
            onSelectContact = {},
            onBack = {},
            onClose = {},
        )
    }
}

data class ViewState(
    val isLoading: Boolean,
    @StringRes val error: Int?,
    val name: String = "",
    val description: String = "",
    val members: List<Contact> = emptyList(),
    val createdGroup: Recipient? = null,
    ) {

    val canCreate
        get() = name.isNotEmpty() && members.isNotEmpty()

    companion object {
        val DEFAULT = ViewState(false, null)
    }

}

sealed class StateUpdate {
    data object Create: StateUpdate()
    data class Name(val value: String): StateUpdate()
    data class Description(val value: String): StateUpdate()
    data class RemoveContact(val value: Contact): StateUpdate()
    data class AddContacts(val value: Set<Contact>): StateUpdate()
}