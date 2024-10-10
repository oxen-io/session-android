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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.squareup.phrase.Phrase
import kotlinx.serialization.Serializable
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.GROUP_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.groups.EditGroupViewModel
import org.thoughtcrime.securesms.groups.GroupMemberState
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.components.ActionAppBar
import org.thoughtcrime.securesms.ui.components.AppBarBackIcon
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineButton
import org.thoughtcrime.securesms.ui.components.SessionOutlinedTextField
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.bold

@Composable
fun EditGroupScreen(
    groupId: AccountId,
    onFinish: () -> Unit,
) {
    val navController = rememberNavController()
    val viewModel = hiltViewModel<EditGroupViewModel, EditGroupViewModel.Factory> { factory ->
        factory.create(groupId)
    }

    NavHost(navController = navController, startDestination = RouteEditGroup) {
        composable<RouteEditGroup> {
            EditGroup(
                onBackClick = onFinish,
                onAddMemberClick = { navController.navigate(RouteSelectContacts) },
                onResendInviteClick = viewModel::onResendInviteClicked,
                onPromoteClick = viewModel::onPromoteContact,
                onRemoveClick = viewModel::onRemoveContact,
                onEditNameClicked = viewModel::onEditNameClicked,
                onEditNameCancelClicked = viewModel::onCancelEditingNameClicked,
                onEditNameConfirmed = viewModel::onEditNameConfirmClicked,
                onEditingNameValueChanged = viewModel::onEditingNameChanged,
                editingName = viewModel.editingName.collectAsState().value,
                members = viewModel.members.collectAsState().value,
                groupName = viewModel.groupName.collectAsState().value,
                showAddMembers = viewModel.showAddMembers.collectAsState().value,
                canEditName = viewModel.canEditGroupName.collectAsState().value,
                onResendPromotionClick = viewModel::onResendPromotionClicked,
                showingError = viewModel.error.collectAsState().value,
                onErrorDismissed = viewModel::onDismissError,
            )
        }

        composable<RouteSelectContacts> {
            SelectContactsScreen(
                excludingAccountIDs = viewModel.excludingAccountIDsFromContactSelection,
                onDoneClicked = {
                    viewModel.onContactSelected(it)
                    navController.popBackStack()
                },
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
    onBackClick: () -> Unit,
    onAddMemberClick: () -> Unit,
    onResendInviteClick: (accountId: String) -> Unit,
    onResendPromotionClick: (accountId: String) -> Unit,
    onPromoteClick: (accountId: String) -> Unit,
    onRemoveClick: (accountId: String, removeMessages: Boolean) -> Unit,
    onEditingNameValueChanged: (String) -> Unit,
    editingName: String?,
    onEditNameClicked: () -> Unit,
    onEditNameConfirmed: () -> Unit,
    onEditNameCancelClicked: () -> Unit,
    canEditName: Boolean,
    groupName: String,
    members: List<GroupMemberState>,
    showAddMembers: Boolean,
    showingError: String?,
    onErrorDismissed: () -> Unit,
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
            ActionAppBar(
                title = stringResource(id = R.string.groupEdit),
                navigationIcon = {
                    AppBarBackIcon(onBack = onBackClick)
                },
                actions = {
                    TextButton(onClick = onBackClick) {
                        Text(
                            text = stringResource(id = R.string.done),
                            color = LocalColors.current.text,
                            style = LocalType.current.large.bold()
                        )
                    }
                },
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
                            contentDescription = stringResource(R.string.AccessibilityId_cancel),
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
                            contentDescription = stringResource(R.string.AccessibilityId_confirm),
                            tint = LocalColors.current.text,
                        )
                    }
                } else {
                    Text(
                        text = groupName,
                        style = LocalType.current.h3,
                        textAlign = TextAlign.Center,
                    )

                    if (canEditName) {
                        IconButton(onClick = onEditNameClicked) {
                            Icon(
                                painterResource(R.drawable.ic_baseline_edit_24),
                                contentDescription = stringResource(R.string.groupName),
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
                    stringResource(R.string.groupMembers),
                    modifier = Modifier.weight(1f),
                    style = LocalType.current.large,
                    color = LocalColors.current.text
                )

                if (showAddMembers) {
                    PrimaryOutlineButton(
                        stringResource(R.string.membersInvite),
                        onClick = onAddMemberClick
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
            onDismissRequest = { setShowingBottomModelForMember(null) },
            sheetState = sheetState,
            onRemove = {
                setShowingConfirmRemovingMember(showingBottomModelForMember)
                setShowingBottomModelForMember(null)
            },
            onPromote = {
                setShowingBottomModelForMember(null)
                onPromoteClick(showingBottomModelForMember.accountId)
            },
            onResendInvite = {
                setShowingBottomModelForMember(null)
                onResendInviteClick(showingBottomModelForMember.accountId)
            },
            onResendPromotion = {
                setShowingBottomModelForMember(null)
                onResendPromotionClick(showingBottomModelForMember.accountId)
            },
            member = showingBottomModelForMember,
        )
    }

    if (showingConfirmRemovingMember != null) {
        ConfirmRemovingMemberDialog(
            onDismissRequest = {
                setShowingConfirmRemovingMember(null)
            },
            onConfirmed = onRemoveClick,
            member = showingConfirmRemovingMember,
            groupName = groupName,
        )
    }

    if (!showingError.isNullOrEmpty()) {
        Snackbar(
            dismissAction = {
                TextButton(onClick = onErrorDismissed) {
                    Text(text = stringResource(id = R.string.dismiss))
                }
            },
            content = {
                Text(text = showingError)
            }
        )
    }
}

@Composable
private fun ConfirmRemovingMemberDialog(
    onConfirmed: (accountId: String, removeMessages: Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    member: GroupMemberState,
    groupName: String,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        text = Phrase.from(context, R.string.groupRemoveDescription)
            .put(NAME_KEY, member.name)
            .put(GROUP_NAME_KEY, groupName)
            .format()
            .toString(),
        title = stringResource(R.string.remove),
        buttons = listOf(
            DialogButtonModel(
                text = GetString(R.string.remove),
                color = LocalColors.current.danger,
                onClick = { onConfirmed(member.accountId, false) }
            ),
            DialogButtonModel(
                text = GetString("Remove and delete messages"), //TODO: Copy
                color = LocalColors.current.danger,
                onClick = { onConfirmed(member.accountId, true) }
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
    onPromote: () -> Unit,
    onResendInvite: () -> Unit,
    onResendPromotion: () -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        if (member.canRemove) {
            val context = LocalContext.current
            MemberModalBottomSheetOptionItem(
                onClick = onRemove,
                text = context.resources.getQuantityString(R.plurals.groupRemoveUserOnly, 1)
            )
        }

        if (member.canPromote) {
            MemberModalBottomSheetOptionItem(
                onClick = onPromote,
                text = stringResource(R.string.adminPromoteToAdmin)
            )
        }

        if (member.canResendInvite) {
            MemberModalBottomSheetOptionItem(onClick = onResendInvite, text = "Resend invite")
        }

        if (member.canResendPromotion) {
            MemberModalBottomSheetOptionItem(onClick = onResendPromotion, text = "Resend promotion")
        }

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

        if (member.canEdit) {
            IconButton(onClick = { onClick(member.accountId) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_circle_dot_dot_dot),
                    contentDescription = stringResource(R.string.AccessibilityId_sessionSettings)
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
            canPromote = true,
            canRemove = true,
            canResendInvite = false,
            canResendPromotion = false,
        )
        val twoMember = GroupMemberState(
            accountId = "05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1235",
            name = "Test User 2",
            status = "Promote failed",
            highlightStatus = true,
            canPromote = true,
            canRemove = true,
            canResendInvite = false,
            canResendPromotion = false,
        )
        val threeMember = GroupMemberState(
            accountId = "05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1236",
            name = "Test User 3",
            status = "",
            highlightStatus = false,
            canPromote = true,
            canRemove = true,
            canResendInvite = false,
            canResendPromotion = false,
        )

        val (editingName, setEditingName) = remember { mutableStateOf<String?>(null) }

        EditGroup(
            onBackClick = {},
            onAddMemberClick = {},
            onResendInviteClick = {},
            onPromoteClick = {},
            onRemoveClick = { _, _ -> },
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
            onResendPromotionClick = {},
            showingError = "Error",
            onErrorDismissed = {}
        )
    }
}