package org.thoughtcrime.securesms.conversation.start.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import network.loki.messenger.R
import org.thoughtcrime.securesms.conversation.start.NullStartConversationDelegate
import org.thoughtcrime.securesms.conversation.start.StartConversationDelegate
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.ItemButton
import org.thoughtcrime.securesms.ui.components.AppBarCloseIcon
import org.thoughtcrime.securesms.ui.components.BasicAppBar
import org.thoughtcrime.securesms.ui.components.QrImage
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.theme.ThemeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StartConversationScreen(
    accountId: String,
    delegate: StartConversationDelegate
) {
    Column(modifier = Modifier.background(
        LocalColors.current.backgroundSecondary,
        shape = MaterialTheme.shapes.small
    )) {
        BasicAppBar(
            title = stringResource(R.string.dialog_start_conversation_title),
            backgroundColor = Color.Transparent, // transparent to show the rounded shape of the container
            actions = { AppBarCloseIcon(onClose = delegate::onDialogClosePressed) }
        )
        Surface(
            modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
            color = LocalColors.current.backgroundSecondary
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                ItemButton(
                    textId = R.string.messageNew,
                    icon = R.drawable.ic_message,
                    modifier = Modifier.contentDescription(R.string.AccessibilityId_new_direct_message),
                    onClick = delegate::onNewMessageSelected)
                Divider(startIndent = LocalDimensions.current.dividerIndent)
                ItemButton(
                    textId = R.string.activity_create_group_title,
                    icon = R.drawable.ic_group,
                    modifier = Modifier.contentDescription(R.string.AccessibilityId_create_group),
                    onClick = delegate::onCreateGroupSelected
                )
                Divider(startIndent = LocalDimensions.current.dividerIndent)
                ItemButton(
                    textId = R.string.dialog_join_community_title,
                    icon = R.drawable.ic_globe,
                    modifier = Modifier.contentDescription(R.string.AccessibilityId_join_community),
                    onClick = delegate::onJoinCommunitySelected
                )
                Divider(startIndent = LocalDimensions.current.dividerIndent)
                ItemButton(
                    textId = R.string.activity_settings_invite_button_title,
                    icon = R.drawable.ic_invite_friend,
                    Modifier.contentDescription(R.string.AccessibilityId_invite_friend_button),
                    onClick = delegate::onInviteFriend
                )
                Column(
                    modifier = Modifier
                        .padding(horizontal = LocalDimensions.current.spacing)
                        .padding(top = LocalDimensions.current.spacing)
                        .padding(bottom = LocalDimensions.current.spacing)
                ) {
                    Text(stringResource(R.string.accountIdYours), style = LocalType.current.xl)
                    Spacer(modifier = Modifier.height(LocalDimensions.current.xxsSpacing))
                    Text(
                        text = stringResource(R.string.qrYoursDescription),
                        color = LocalColors.current.textSecondary,
                        style = LocalType.current.small
                    )
                    Spacer(modifier = Modifier.height(LocalDimensions.current.smallSpacing))
                    QrImage(
                        string = accountId,
                        Modifier.contentDescription(R.string.AccessibilityId_qr_code),
                        icon = R.drawable.session
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewStartConversationScreen(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        StartConversationScreen(
            accountId = "059287129387123",
            NullStartConversationDelegate
        )
    }
}
