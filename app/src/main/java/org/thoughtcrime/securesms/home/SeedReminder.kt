package org.thoughtcrime.securesms.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.SessionShieldIcon
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.components.SlimPrimaryOutlineButton
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.h8
import org.thoughtcrime.securesms.ui.small

@Composable
internal fun SeedReminder(startRecoveryPasswordActivity: () -> Unit) {
    Column {
        // Color Strip
        Box(
            Modifier
                .fillMaxWidth()
                .height(LocalDimensions.current.indicatorHeight)
                .background(LocalColors.current.primary)
        )
        Row(
            Modifier
                .background(LocalColors.current.backgroundSecondary)
                .padding(
                    horizontal = LocalDimensions.current.smallMargin,
                    vertical = LocalDimensions.current.xsMargin
                )
        ) {
            Column(Modifier.weight(1f)) {
                Row {
                    Text(
                        stringResource(R.string.save_your_recovery_password),
                        style = h8
                    )
                    Spacer(Modifier.requiredWidth(LocalDimensions.current.xxsItemSpacing))
                    SessionShieldIcon()
                }
                Text(
                    stringResource(R.string.save_your_recovery_password_to_make_sure_you_don_t_lose_access_to_your_account),
                    style = small
                )
            }
            Spacer(Modifier.width(LocalDimensions.current.xxsMargin))
            SlimPrimaryOutlineButton(
                text = stringResource(R.string.continue_2),
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .contentDescription(R.string.AccessibilityId_reveal_recovery_phrase_button),
                onClick = startRecoveryPasswordActivity
            )
        }
    }
}

@Preview
@Composable
private fun PreviewSeedReminder(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: Colors
) {
    PreviewTheme(colors) {
        SeedReminder {}
    }
}
