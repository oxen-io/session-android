package org.thoughtcrime.securesms.groups.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.squareup.phrase.Phrase
import kotlinx.serialization.Serializable
import network.loki.messenger.R
import org.thoughtcrime.securesms.groups.EditGroupViewModel
import org.thoughtcrime.securesms.groups.GroupMemberState
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.NavigationBar
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineButton
import org.thoughtcrime.securesms.ui.components.SessionOutlinedTextField
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.bold

@Composable
fun EditGroupScreen(
    groupSessionId: String,
    onFinish: () -> Unit,
) {
    val navController = rememberNavController()
    val viewModel = hiltViewModel<EditGroupViewModel, EditGroupViewModel.Factory> { factory ->
        factory.create(groupSessionId)
    }

    NavHost(navController = navController, startDestination = RouteEditGroup) {
        composable<RouteEditGroup> {
            EditGroup(
                onBack = onFinish,
                onInvite = {
                    navController.navigate(RouteSelectContacts())
                },
                onReinvite = viewModel::onReInviteContact,
                onPromote = viewModel::onPromoteContact,
                onRemove = viewModel::onRemoveContact,
                onEditNameClicked = viewModel::onEditNameClicked,
                onEditNameCancelClicked = viewModel::onCancelEditingNameClicked,
                onEditNameConfirmed = viewModel::onEditNameConfirmClicked,
                onEditingNameValueChanged = viewModel::onEditingNameChanged,
                editingName = viewModel.editingName.collectAsState().value,
                members = viewModel.members.collectAsState().value,
                groupName = viewModel.groupName.collectAsState().value,
                showAddMembers = viewModel.showAddMembers.collectAsState().value,
                canEditName = viewModel.canEditGroupName.collectAsState().value,
            )
        }

        composable<RouteSelectContacts> { entry ->
            val route: RouteSelectContacts = entry.toRoute()

            SelectContactsScreen(
                onlySelectFromAccountIDs = route.onlySelectFromAccountIDs?.toSet(),
                onDoneClicked = viewModel::onContactSelected,
                onBackClicked = { navController.popBackStack() },
            )
        }
    }

}

@Serializable
private object RouteEditGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGroup(
    onBack: () -> Unit,
    onInvite: () -> Unit,
    onReinvite: (accountId: String) -> Unit,
    onPromote: (accountId: String) -> Unit,
    onRemove: (accountId: String) -> Unit,
    onEditingNameValueChanged: (String) -> Unit,
    editingName: String?,
    onEditNameClicked: () -> Unit,
    onEditNameConfirmed: () -> Unit,
    onEditNameCancelClicked: () -> Unit,
    canEditName: Boolean,
    groupName: String,
    members: List<GroupMemberState>,
    showAddMembers: Boolean,
) {
    val sheetState = rememberModalBottomSheetState()

    val (showingBottomModelForMember, setShowingBottomModelForMember) = remember {
        mutableStateOf<GroupMemberState?>(null)
    }

    val (showingConfirmRemovingMember, setShowingConfirmRemovingMember) = remember {
        mutableStateOf<GroupMemberState?>(null)
    }

    Scaffold(
        topBar = {
            NavigationBar(
                title = stringResource(id = R.string.activity_edit_closed_group_title),
                onBack = onBack,
                actionElement = {
                    TextButton(onClick = onBack) {
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
                    .animateContentSize()
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = CenterVertically,
            ) {
                if (editingName != null) {
                    IconButton(onClick = onEditNameCancelClicked) {
                        Icon(
                            painter = painterResource(R.drawable.ic_x),
                            contentDescription = stringResource(R.string.AccessibilityId_cancel_name_change),
                            tint = LocalColors.current.text,
                        )
                    }

                    SessionOutlinedTextField(
                        modifier = Modifier.width(180.dp),
                        text = editingName,
                        onChange = onEditingNameValueChanged,
                        textStyle = LocalType.current.large
                    )

                    IconButton(onClick = onEditNameConfirmed) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = stringResource(R.string.AccessibilityId_accept_name_change),
                            tint = LocalColors.current.text,
                        )
                    }
                } else {
                    val nameDesc = stringResource(R.string.AccessibilityId_group_name)
                    Text(
                        text = groupName,
                        style = LocalType.current.h3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics {
                            contentDescription = nameDesc
                        }
                    )

                    if (canEditName) {
                        IconButton(onClick = onEditNameClicked) {
                            Icon(
                                painterResource(R.drawable.ic_baseline_edit_24),
                                contentDescription = stringResource(R.string.AccessibilityId_closed_group_edit_group_name),
                                tint = LocalColors.current.text,
                            )
                        }
                    }
                }
            }

            // Header & Add member button
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = CenterVertically
            ) {
                Text(
                    stringResource(R.string.activity_edit_closed_group_edit_members),
                    modifier = Modifier.weight(1f),
                    style = LocalType.current.large,
                    color = LocalColors.current.text
                )

                if (showAddMembers) {
                    PrimaryOutlineButton(
                        stringResource(R.string.activity_edit_closed_group_add_members),
                        onClick = onInvite
                    )
                }
            }


            // List of members
            LazyColumn(modifier = Modifier) {
                items(members) { member ->
                    // Each member's view
                    MemberItem(
                        modifier = Modifier.fillMaxWidth(),
                        member = member,
                        onClick = { setShowingBottomModelForMember(member) }
                    )
                }
            }
        }
    }

    if (showingBottomModelForMember != null) {
        MemberModalBottomSheetOptions(
            member = showingBottomModelForMember,
            onDismissRequest = { setShowingBottomModelForMember(null) },
            sheetState = sheetState,
            onRemove = {
                setShowingConfirmRemovingMember(showingBottomModelForMember)
                setShowingBottomModelForMember(null)
            },
        )
    }

    if (showingConfirmRemovingMember != null) {
        ConfirmRemovingMemberDialog(
            onDismissRequest = {
                setShowingConfirmRemovingMember(null)
            },
            onConfirmed = onRemove,
            member = showingConfirmRemovingMember,
            groupName = groupName,
        )
    }
}

@Composable
private fun ConfirmRemovingMemberDialog(
    onConfirmed: (accountId: String) -> Unit,
    onDismissRequest: () -> Unit,
    member: GroupMemberState,
    groupName: String,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        text = Phrase.from(context, R.string.activity_edit_closed_group_remove_users_single)
            .put("user", member.name)
            .put("group", groupName)
            .format()
            .toString(),
        title = stringResource(R.string.activity_settings_remove),
        buttons = listOf(
            DialogButtonModel(
                text = GetString(R.string.activity_settings_remove),
                color = LocalColors.current.danger,
                onClick = { onConfirmed(member.accountId) }
            ),
            DialogButtonModel(
                text = GetString(R.string.cancel),
                onClick = onDismissRequest,
            )
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberModalBottomSheetOptions(
    member: GroupMemberState,
    onRemove: () -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        MemberModalBottomSheetOptionItem(
            onClick = onRemove,
            text = stringResource(R.string.fragment_edit_group_bottom_sheet_remove)
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun MemberModalBottomSheetOptionItem(
    text: String,
    onClick: () -> Unit
) {
    Text(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(16.dp)
            .fillMaxWidth(),
        style = LocalType.current.base,
        text = text,
        color = LocalColors.current.text,
    )
}

@Composable
private fun MemberItem(
    onClick: (accountId: String) -> Unit,
    member: GroupMemberState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = CenterVertically,
    ) {
        ContactPhoto(member.accountId)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            Text(
                style = LocalType.current.large,
                text = member.name,
                color = LocalColors.current.text
            )

            if (member.status.isNotEmpty()) {
                Text(
                    text = member.status,
                    style = LocalType.current.small,
                    color = if (member.highlightStatus) {
                        LocalColors.current.danger
                    } else {
                        LocalColors.current.textSecondary
                    },
                )
            }
        }

        if (member.showEdit) {
            IconButton(onClick = { onClick(member.accountId) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_circle_dot_dot_dot),
                    contentDescription = stringResource(R.string.AccessibilityId_settings)
                )
            }
        }
    }
}


@Preview
@Composable
private fun EditGroupPreview() {
    PreviewTheme {
        val oneMember = GroupMemberState(
            accountId = "05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234",
            name = "Test User",
            status = "Invited",
            highlightStatus = false,
            showEdit = true
        )
        val twoMember = GroupMemberState(
            accountId = "05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1235",
            name = "Test User 2",
            status = "Promote failed",
            highlightStatus = true,
            showEdit = true
        )
        val threeMember = GroupMemberState(
            accountId = "05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1236",
            name = "Test User 3",
            status = "",
            highlightStatus = false,
            showEdit = true
        )

        val (editingName, setEditingName) = remember { mutableStateOf<String?>(null) }

        EditGroup(
            onBack = {},
            onInvite = {},
            onReinvite = {},
            onPromote = {},
            onRemove = {},
            onEditNameCancelClicked = {
                setEditingName(null)
            },
            onEditNameConfirmed = {
                setEditingName(null)
            },
            onEditNameClicked = {
                setEditingName("Test Group")
            },
            editingName = editingName,
            onEditingNameValueChanged = setEditingName,
            members = listOf(oneMember, twoMember, threeMember),
            canEditName = true,
            groupName = "Test",
            showAddMembers = true,
        )
    }
}