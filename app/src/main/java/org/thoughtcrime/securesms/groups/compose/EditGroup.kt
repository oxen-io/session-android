package org.thoughtcrime.securesms.groups.compose

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.color.MaterialColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.NavResult
import com.ramcosta.composedestinations.result.ResultBackNavigator
import com.ramcosta.composedestinations.result.ResultRecipient
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.thoughtcrime.securesms.groups.ContactList
import org.thoughtcrime.securesms.groups.EditGroupEvent
import org.thoughtcrime.securesms.groups.EditGroupInviteViewModel
import org.thoughtcrime.securesms.groups.EditGroupViewModel
import org.thoughtcrime.securesms.groups.EditGroupViewState
import org.thoughtcrime.securesms.groups.MemberState
import org.thoughtcrime.securesms.groups.MemberViewModel
import org.thoughtcrime.securesms.groups.destinations.EditClosedGroupInviteScreenDestination
import org.thoughtcrime.securesms.groups.destinations.EditClosedGroupNameScreenDestination
import org.thoughtcrime.securesms.groups.toDisplayColor
import org.thoughtcrime.securesms.groups.toDisplayString
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.ui.CellWithPaddingAndMargin
import org.thoughtcrime.securesms.ui.NavigationBar
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.bold

@EditGroupNavGraph(start = true)
@Composable
@Destination
fun EditClosedGroupScreen(
    navigator: DestinationsNavigator,
    resultSelectContact: ResultRecipient<EditClosedGroupInviteScreenDestination, ContactList>,
    resultEditName: ResultRecipient<EditClosedGroupNameScreenDestination, String>,
    viewModel: EditGroupViewModel,
    onFinish: () -> Unit
) {
    val group by viewModel.viewState.collectAsState()
    val context = LocalContext.current
    val viewState = group.viewState
    val eventSink = group.eventSink

    resultSelectContact.onNavResult { navResult ->
        if (navResult is NavResult.Value) {
            eventSink(EditGroupEvent.InviteContacts(context, navResult.value))
        }
    }

    resultEditName.onNavResult { navResult ->
        if (navResult is NavResult.Value) {
            eventSink(EditGroupEvent.ChangeName(navResult.value))
        }
    }

    EditGroupView(
        onBack = {
            onFinish()
        },
        onInvite = {
            navigator.navigate(EditClosedGroupInviteScreenDestination)
        },
        onReinvite = { contact ->
            eventSink(EditGroupEvent.ReInviteContact(contact))
        },
        onPromote = { contact ->
            eventSink(EditGroupEvent.PromoteContact(contact))
        },
        onRemove = { contact ->
            val string = Phrase.from(context, R.string.activity_edit_closed_group_remove_users_single)
                .put("user", contact.memberName)
                .put("group", viewState.groupName)
                .format()
            context.showSessionDialog {
                title(R.string.activity_settings_remove)
                text(string)
                dangerButton(R.string.activity_settings_remove) {
                    eventSink(EditGroupEvent.RemoveContact(contact.memberSessionId))
                }
                cancelButton()
            }
        },
        onEditName = {
            navigator.navigate(EditClosedGroupNameScreenDestination)
        },
        onMemberSelected = { member ->
            if (member.memberState == MemberState.Admin) {
                // show toast saying we can't remove them
                Toast.makeText(context,
                    R.string.ConversationItem_group_member_admin_cannot_remove,
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        viewState = viewState
    )
}

@EditGroupNavGraph
@Composable
@Destination
fun EditClosedGroupInviteScreen(
    resultNavigator: ResultBackNavigator<ContactList>,
    viewModel: EditGroupInviteViewModel,
) {

    val state by viewModel.viewState.collectAsState()
    val viewState = state.viewState
    val currentMemberSessionIds = viewState.currentMembers.map { it.memberSessionId }

    SelectContacts(
        viewState.allContacts
            .filterNot { it.accountID in currentMemberSessionIds }
            .toSet(),
        onBack = { resultNavigator.navigateBack() },
        onContactsSelected = {
            resultNavigator.navigateBack(ContactList(it))
        },
    )
}


@Composable
fun EditGroupView(
    onBack: ()->Unit,
    onInvite: ()->Unit,
    onReinvite: (String)->Unit,
    onPromote: (String)->Unit,
    onRemove: (MemberViewModel)->Unit,
    onEditName: ()->Unit,
    onMemberSelected: (MemberViewModel) -> Unit,
    viewState: EditGroupViewState,
) {

    Scaffold(
        topBar = {
            NavigationBar(
                title = stringResource(id = R.string.activity_edit_closed_group_title),
                onBack = onBack,
                actionElement = {
                    TextButton(onClick = { onBack() }) {
                        Text(
                            text = stringResource(id = R.string.menu_done_button),
                            color = LocalColors.current.text,
                            style = LocalType.current.large.bold()
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {

            GroupMinimumVersionBanner()

            // Group name title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                val nameDesc = stringResource(R.string.AccessibilityId_group_name)
                Text(
                    text = viewState.groupName,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics {
                        contentDescription = nameDesc
                    }
                )
                if (viewState.admin) {
                    Icon(
                        painterResource(R.drawable.ic_baseline_edit_24),
                        null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .align(CenterVertically)
                            .clickable { onEditName() }
                    )
                }
            }
            // Description
            if (viewState.groupDescription?.isNotEmpty() == true) {
                val descriptionDesc = stringResource(R.string.AccessibilityId_group_description)
                Text(
                    text = viewState.groupDescription,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .semantics {
                            contentDescription = descriptionDesc
                        }
                )
            }

            // Invite
            if (viewState.admin) {
                CellWithPaddingAndMargin(margin = 16.dp, padding = 16.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onInvite)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = CenterVertically,
                    ) {
                        Icon(painterResource(id = R.drawable.ic_add_admins), contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = stringResource(id = R.string.activity_edit_closed_group_add_members))
                    }
                }
            }
            // members header
            Text(
                text = stringResource(id = R.string.conversation_settings_group_members),
//                style = MaterialTheme.typography.subtitle2,
                modifier = Modifier
                    .padding(vertical = 8.dp, horizontal = 32.dp)
            )
            // List of members
            LazyColumn(modifier = Modifier) {

                items(viewState.memberStateList) { member ->
                    // Each member's view
                    MemberItem(
                        isAdmin = viewState.admin,
                        member = member,
                        onReinvite = onReinvite,
                        onPromote = onPromote,
                        onRemove = onRemove,
                        onMemberSelected = onMemberSelected
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemberItem(modifier: Modifier = Modifier,
               isAdmin: Boolean,
               member: MemberViewModel,
               onReinvite: (String) -> Unit,
               onPromote: (String) -> Unit,
               onRemove: (MemberViewModel) -> Unit,
               onMemberSelected: (MemberViewModel) -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onLongClick = {
                // long pressing should remove the member
                onRemove(member)
            }, onClick = {
                // handle clicking the member
                onMemberSelected(member)
            })
            .padding(vertical = 8.dp, horizontal = 16.dp)) {
        ContactPhoto(member.memberSessionId)
        Column(modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 8.dp)
            .align(CenterVertically)) {
            // Member's name
            val memberDesc = stringResource(R.string.AccessibilityId_contact)
            Text(
                text = member.memberName ?: member.memberSessionId,
                style = MemberNameStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(1.dp)
                    .semantics {
                        contentDescription = memberDesc
                    }
            )
            member.memberState.toDisplayString()?.let { displayString ->
                // Display the current member state
                val stateDesc = stringResource(R.string.AccessibilityId_member_state)
                Text(
                    text = displayString,
                    color = member.memberState.toDisplayColor(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(1.dp)
                        .semantics {
                            contentDescription = stateDesc
                        }
                )
            }
        }
        // Resend button
        if (isAdmin && member.memberState == MemberState.InviteFailed) {
            val reinviteDesc = stringResource(R.string.AccessibilityId_reinvite_member)
            TextButton(
                onClick = {
                    onReinvite(member.memberSessionId)
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .controlHighlightBackground()
                    .semantics {
                        contentDescription = reinviteDesc
                    },
                contentPadding = PaddingValues(8.dp,2.dp)
            ) {
                Text(
                    stringResource(id = R.string.EditGroup_resend_action),
                    color = LocalColors.current.text
                )
            }
        } else if (isAdmin && member.memberState == MemberState.Member) {
            // Promotion button
            val promoteDesc = stringResource(R.string.AccessibilityId_promote_member)
            TextButton(
                onClick = {
                    onPromote(member.memberSessionId)
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .controlHighlightBackground()
                    .semantics {
                        contentDescription = promoteDesc
                    },
                contentPadding = PaddingValues(8.dp,2.dp)
            ) {
                Text(
                    stringResource(R.string.EditGroup_promote_action),
                    color = LocalColors.current.text
                )
            }
        }
    }

}

@Composable
fun Modifier.controlHighlightBackground() = this.background(
    Color(
        MaterialColors.getColor(
            LocalContext.current,
            R.attr.colorControlHighlight,
            LocalColors.current.text.toArgb()
        )
    )
)

@Preview
@Composable
fun PreviewList() {
    PreviewTheme {
        val oneMember = MemberViewModel(
            "Test User",
            "05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234",
            MemberState.InviteSent,
            false
        )
        val twoMember = MemberViewModel(
            "Test User 2",
            "05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1235",
            MemberState.InviteFailed,
            false
        )
        val threeMember = MemberViewModel(
            "Test User 3",
            "05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1236",
            MemberState.Member,
            false
        )

        val viewState = EditGroupViewState(
            "Preview",
            "This is a preview description",
            listOf(oneMember, twoMember, threeMember),
            true
        )

        EditGroupView(
            onBack = {},
            onInvite = {},
            onReinvite = {},
            onPromote = {},
            onRemove = {},
            onEditName = {},
            onMemberSelected = {},
            viewState = viewState
        )
    }
}