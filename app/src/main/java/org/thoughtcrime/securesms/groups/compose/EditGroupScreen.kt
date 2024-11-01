package org.thoughtcrime.securesms.groups.compose

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import network.loki.messenger.BuildConfig
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.GROUP_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.groups.EditGroupViewModel
import org.thoughtcrime.securesms.groups.GroupMemberState
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.components.BackAppBar
import org.thoughtcrime.securesms.ui.components.BottomOptionsDialog
import org.thoughtcrime.securesms.ui.components.BottomOptionsDialogItem
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineButton
import org.thoughtcrime.securesms.ui.components.SessionOutlinedTextField
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme

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
    onResendInviteClick: (accountId: AccountId) -> Unit,
    onResendPromotionClick: (accountId: AccountId) -> Unit,
    onPromoteClick: (accountId: AccountId) -> Unit,
    onRemoveClick: (accountId: AccountId, removeMessages: Boolean) -> Unit,
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
    val (showingOptionsDialogForMember, setShowingBottomModelForMember) = remember {
        mutableStateOf<GroupMemberState?>(null)
    }

    val (showingConfirmRemovingMember, setShowingConfirmRemovingMember) = remember {
        mutableStateOf<GroupMemberState?>(null)
    }

    Scaffold(
        topBar = {
            BackAppBar(
                title = stringResource(id = R.string.groupEdit),
                onBack = onBackClick,
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {

            GroupMinimumVersionBanner()

            // Group name title
            Crossfade(editingName != null, label = "Editable group name") { showNameEditing ->
                if (showNameEditing) {
                    GroupNameContainer {
                        IconButton(
                            modifier = Modifier.size(LocalDimensions.current.spacing),
                            onClick = onEditNameCancelClicked) {
                            Icon(
                                painter = painterResource(R.drawable.ic_x),
                                contentDescription = stringResource(R.string.AccessibilityId_cancel),
                                tint = LocalColors.current.text,
                            )
                        }

                        SessionOutlinedTextField(
                            modifier = Modifier.widthIn(
                                min = LocalDimensions.current.mediumSpacing,
                                max = 240.dp
                            ),
                            text = editingName.orEmpty(),
                            onChange = onEditingNameValueChanged,
                            textStyle = LocalType.current.h8,
                            singleLine = true,
                            innerPadding = PaddingValues(
                                horizontal = LocalDimensions.current.spacing,
                                vertical = LocalDimensions.current.smallSpacing
                            )
                        )

                        IconButton(
                            modifier = Modifier.size(LocalDimensions.current.spacing),
                            onClick = onEditNameConfirmed) {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = stringResource(R.string.AccessibilityId_confirm),
                                tint = LocalColors.current.text,
                            )
                        }
                    }


                } else {
                    GroupNameContainer {
                        Text(
                            text = groupName,
                            style = LocalType.current.h4,
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
            }

            // Header & Add member button
            Row(
                modifier = Modifier.padding(
                    horizontal = LocalDimensions.current.smallSpacing,
                    vertical = LocalDimensions.current.xxsSpacing
                ),
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
                        clickable = member.canEdit,
                        onClick = { setShowingBottomModelForMember(member) }
                    )
                }
            }
        }
    }

    if (showingOptionsDialogForMember != null) {
        MemberOptionsDialog(
            onDismissRequest = { setShowingBottomModelForMember(null) },
            onRemove = {
                setShowingConfirmRemovingMember(showingOptionsDialogForMember)
                setShowingBottomModelForMember(null)
            },
            onPromote = {
                setShowingBottomModelForMember(null)
                onPromoteClick(showingOptionsDialogForMember.accountId)
            },
            onResendInvite = {
                setShowingBottomModelForMember(null)
                onResendInviteClick(showingOptionsDialogForMember.accountId)
            },
            onResendPromotion = {
                setShowingBottomModelForMember(null)
                onResendPromotionClick(showingOptionsDialogForMember.accountId)
            },
            member = showingOptionsDialogForMember,
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

    val context = LocalContext.current

    LaunchedEffect(showingError) {
        if (showingError != null) {
            Toast.makeText(context, showingError, Toast.LENGTH_SHORT).show()
            onErrorDismissed()
        }
    }
}

@Composable
private fun GroupNameContainer(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        horizontalArrangement = Arrangement.spacedBy(
            LocalDimensions.current.xxxsSpacing,
            Alignment.CenterHorizontally
        ),
        verticalAlignment = CenterVertically,
        content = content
    )
}

@Composable
private fun ConfirmRemovingMemberDialog(
    onConfirmed: (accountId: AccountId, removeMessages: Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    member: GroupMemberState,
    groupName: String,
) {
    val context = LocalContext.current
    val buttons = buildList {
        this += DialogButtonModel(
            text = GetString(R.string.remove),
            color = LocalColors.current.danger,
            onClick = { onConfirmed(member.accountId, false) }
        )

        if (BuildConfig.DEBUG) {
            this += DialogButtonModel(
                text = GetString("Remove with messages"),
                color = LocalColors.current.danger,
                onClick = { onConfirmed(member.accountId, true) }
            )
        }

        this += DialogButtonModel(
            text = GetString(R.string.cancel),
            onClick = onDismissRequest,
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        text = Phrase.from(context, R.string.groupRemoveDescription)
            .put(NAME_KEY, member.name)
            .put(GROUP_NAME_KEY, groupName)
            .format()
            .toString(),
        title = stringResource(R.string.remove),
        buttons = buttons
    )
}

@Composable
private fun MemberOptionsDialog(
    member: GroupMemberState,
    onRemove: () -> Unit,
    onPromote: () -> Unit,
    onResendInvite: () -> Unit,
    onResendPromotion: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current

    val options = remember(member) {
        buildList {
            if (member.canRemove) {
                this += BottomOptionsDialogItem(
                    title = context.resources.getQuantityString(R.plurals.groupRemoveUserOnly, 1),
                    iconRes = R.drawable.ic_delete_24,
                    onClick = onRemove
                )
            }

            if (BuildConfig.DEBUG && member.canPromote) {
                this += BottomOptionsDialogItem(
                    title = context.getString(R.string.adminPromoteToAdmin),
                    iconRes = R.drawable.ic_profile_default,
                    onClick = onPromote
                )
            }

            if (BuildConfig.DEBUG && member.canResendInvite) {
                this += BottomOptionsDialogItem(
                    title = "Resend invitation",
                    iconRes = R.drawable.ic_mail,
                    onClick = onResendInvite
                )
            }

            if (BuildConfig.DEBUG && member.canResendPromotion) {
                this += BottomOptionsDialogItem(
                    title = "Resend promotion",
                    iconRes = R.drawable.ic_mail,
                    onClick = onResendPromotion
                )
            }
        }
    }

    BottomOptionsDialog(
        items = options,
        onDismissRequest = onDismissRequest
    )
}

@Composable
private fun MemberItem(
    clickable: Boolean,
    onClick: (accountId: AccountId) -> Unit,
    member: GroupMemberState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(enabled = clickable, onClick = { onClick(member.accountId) })
            .padding(
                horizontal = LocalDimensions.current.smallSpacing,
                vertical = LocalDimensions.current.xsSpacing
            ),
        horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallSpacing),
        verticalAlignment = CenterVertically,
    ) {
        ContactPhoto(member.accountId)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.xxxsSpacing)
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
            Icon(
                painter = painterResource(R.drawable.ic_circle_dot_dot_dot),
                contentDescription = stringResource(R.string.AccessibilityId_sessionSettings)
            )
        }
    }
}


@Preview
@Composable
private fun EditGroupPreview() {
    PreviewTheme {
        val oneMember = GroupMemberState(
            accountId = AccountId("05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234"),
            name = "Test User",
            status = "Invited",
            highlightStatus = false,
            canPromote = true,
            canRemove = true,
            canResendInvite = false,
            canResendPromotion = false,
        )
        val twoMember = GroupMemberState(
            accountId = AccountId("05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1235"),
            name = "Test User 2",
            status = "Promote failed",
            highlightStatus = true,
            canPromote = true,
            canRemove = true,
            canResendInvite = false,
            canResendPromotion = false,
        )
        val threeMember = GroupMemberState(
            accountId = AccountId("05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1236"),
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