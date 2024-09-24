package org.thoughtcrime.securesms.conversation.start.invitefriend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.thoughtcrime.securesms.ui.components.AppBarCloseIcon
import org.thoughtcrime.securesms.ui.components.BackAppBar
import org.thoughtcrime.securesms.ui.components.SlimOutlineButton
import org.thoughtcrime.securesms.ui.components.SlimOutlineCopyButton
import org.thoughtcrime.securesms.ui.components.border
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InviteFriend(
    accountId: String,
    onBack: () -> Unit = {},
    onClose: () -> Unit = {},
    copyPublicKey: () -> Unit = {},
    sendInvitation: () -> Unit = {},
) {
    Column(modifier = Modifier.background(
        LocalColors.current.backgroundSecondary,
        shape = MaterialTheme.shapes.small
    )) {
        BackAppBar(
            title = stringResource(R.string.sessionInviteAFriend),
            backgroundColor = Color.Transparent, // transparent to show the rounded shape of the container
            onBack = onBack,
            actions = { AppBarCloseIcon(onClose = onClose) }
        )
        Column(
            modifier = Modifier.padding(horizontal = LocalDimensions.current.spacing)
                .padding(top = LocalDimensions.current.spacing),
        ) {
            Text(
                accountId,
                modifier = Modifier
                    .contentDescription(R.string.AccessibilityId_shareAccountId)
                    .fillMaxWidth()
                    .border()
                    .padding(LocalDimensions.current.spacing),
                textAlign = TextAlign.Center,
                style = LocalType.current.base
            )

            Spacer(modifier = Modifier.height(LocalDimensions.current.xsSpacing))

            Text(
                stringResource(R.string.shareAccountIdDescription).let { txt ->
                    val c = LocalContext.current
                    Phrase.from(txt).put(APP_NAME_KEY, c.getString(R.string.app_name)).format().toString()
                },
                textAlign = TextAlign.Center,
                style = LocalType.current.small,
                color = LocalColors.current.textSecondary,
                modifier = Modifier.padding(horizontal = LocalDimensions.current.smallSpacing)
            )

            Spacer(modifier = Modifier.height(LocalDimensions.current.smallSpacing))

            Row(horizontalArrangement = spacedBy(LocalDimensions.current.smallSpacing)) {
                SlimOutlineButton(
                    stringResource(R.string.share),
                    modifier = Modifier
                        .weight(1f)
                        .contentDescription("Share button"),
                    onClick = sendInvitation
                )

                SlimOutlineCopyButton(
                    modifier = Modifier.weight(1f),
                    onClick = copyPublicKey
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewInviteFriend() {
    PreviewTheme {
        InviteFriend("050000000")
    }
}
