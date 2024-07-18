package org.thoughtcrime.securesms.onboarding.messagenotifications

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.thoughtcrime.securesms.onboarding.OnboardingBackPressAlertDialog
import org.thoughtcrime.securesms.onboarding.messagenotifications.MessageNotificationsViewModel.UiState
import org.thoughtcrime.securesms.onboarding.ui.ContinuePrimaryOutlineButton
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.components.CircularProgressIndicator
import org.thoughtcrime.securesms.ui.components.RadioButton
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.h4
import org.thoughtcrime.securesms.ui.h8
import org.thoughtcrime.securesms.ui.h9
import org.thoughtcrime.securesms.ui.small

@Composable
internal fun MessageNotificationsScreen(
    state: UiState = UiState(),
    setEnabled: (Boolean) -> Unit = {},
    onContinue: () -> Unit = {},
    quit: () -> Unit = {},
    dismissDialog: () -> Unit = {}
) {
    if (state.clearData) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(LocalColors.current.primary)
        }

        return
    }

    if (state.showDialog) OnboardingBackPressAlertDialog(dismissDialog, quit = quit)

    Column {
        Spacer(Modifier.weight(1f))

        Column(modifier = Modifier.padding(horizontal = LocalDimensions.current.onboardingMargin)) {
            Text(stringResource(R.string.notificationsMessage), style = h4)
            Spacer(Modifier.height(LocalDimensions.current.xsMargin))
            Text(stringResource(R.string.onboardingMessageNotificationExplaination), style = base)
            Spacer(Modifier.height(LocalDimensions.current.itemSpacing))
        }

        NotificationRadioButton(
            R.string.activity_pn_mode_fast_mode,
            R.string.activity_pn_mode_fast_mode_explanation,
            modifier = Modifier.contentDescription(R.string.AccessibilityId_fast_mode_notifications_button),
            tag = R.string.activity_pn_mode_recommended_option_tag,
            checked = state.pushEnabled,
            onClick = { setEnabled(true) }
        )

        // spacing between buttons is provided by ripple/downstate of NotificationRadioButton

        NotificationRadioButton(
            R.string.activity_pn_mode_slow_mode,
            R.string.activity_pn_mode_slow_mode_explanation,
            modifier = Modifier.contentDescription(R.string.AccessibilityId_slow_mode_notifications_button),
            checked = state.pushDisabled,
            onClick = { setEnabled(false) }
        )

        Spacer(Modifier.weight(1f))

        ContinuePrimaryOutlineButton(Modifier.align(Alignment.CenterHorizontally), onContinue)
    }
}

@Composable
private fun NotificationRadioButton(
    @StringRes title: Int,
    @StringRes explanation: Int,
    modifier: Modifier = Modifier,
    @StringRes tag: Int? = null,
    checked: Boolean = false,
    onClick: () -> Unit = {}
) {
    RadioButton(
        onClick = onClick,
        modifier = modifier,
        checked = checked,
        contentPadding = PaddingValues(horizontal = LocalDimensions.current.margin, vertical = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .border(
                    LocalDimensions.current.borderStroke,
                    LocalColors.current.borders,
                    RoundedCornerShape(8.dp)
                ),
        ) {
            Column(modifier = Modifier
                .padding(horizontal = 15.dp)
                .padding(top = 10.dp, bottom = 11.dp)) {
                Text(stringResource(title), style = h8)

                Text(stringResource(explanation), style = small, modifier = Modifier.padding(top = 7.dp))
                tag?.let {
                    Text(
                        stringResource(it),
                        modifier = Modifier.padding(top = 6.dp),
                        color = LocalColors.current.primary,
                        style = h9
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun MessageNotificationsScreenPreview(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: Colors
) {
    PreviewTheme(colors) {
        MessageNotificationsScreen()
    }
}
