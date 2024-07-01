package org.thoughtcrime.securesms.conversation.start.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.components.AppBar
import org.thoughtcrime.securesms.ui.components.QrImage
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.small
import org.thoughtcrime.securesms.ui.xl

@Composable
internal fun StartConversationScreen(
    accountId: String,
    delegate: StartConversationDelegate
) {
    Column(modifier = Modifier.background(LocalColors.current.backgroundSecondary)) {
        AppBar(stringResource(R.string.dialog_start_conversation_title), onClose = delegate::onDialogClosePressed)
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
                    onClick = delegate::onNewMessageSelected)
                Divider(startIndent = LocalDimensions.current.dividerIndent)
                ItemButton(
                    textId = R.string.activity_create_group_title,
                    icon = R.drawable.ic_group,
                    onClick = delegate::onCreateGroupSelected
                )
                Divider(startIndent = LocalDimensions.current.dividerIndent)
                ItemButton(
                    textId = R.string.dialog_join_community_title,
                    icon = R.drawable.ic_globe,
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
                        .padding(horizontal = LocalDimensions.current.margin)
                        .padding(top = LocalDimensions.current.itemSpacing)
                        .padding(bottom = LocalDimensions.current.margin)
                ) {
                    Text(stringResource(R.string.accountIdYours), style = xl)
                    Spacer(modifier = Modifier.height(LocalDimensions.current.xxxsItemSpacing))
                    Text(
                        text = stringResource(R.string.qrYoursDescription),
                        color = LocalColors.current.textSecondary,
                        style = small
                    )
                    Spacer(modifier = Modifier.height(LocalDimensions.current.smallItemSpacing))
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
    @PreviewParameter(SessionColorsParameterProvider::class) colors: Colors
) {
    PreviewTheme(colors) {
        StartConversationScreen(
            accountId = "059287129387123",
            NullStartConversationDelegate
        )
    }
}
