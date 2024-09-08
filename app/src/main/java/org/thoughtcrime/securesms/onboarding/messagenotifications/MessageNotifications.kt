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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.thoughtcrime.securesms.onboarding.OnboardingBackPressAlertDialog
import org.thoughtcrime.securesms.onboarding.messagenotifications.MessageNotificationsViewModel.UiState
import org.thoughtcrime.securesms.onboarding.ui.ContinuePrimaryOutlineButton
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.theme.ThemeColors
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.components.CircularProgressIndicator
import org.thoughtcrime.securesms.ui.components.RadioButton
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.theme.LocalType

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
            CircularProgressIndicator(color = LocalColors.current.primary)
        }

        return
    }

    if (state.showingBackWarningDialogText != null)  {
        OnboardingBackPressAlertDialog(dismissDialog,
            textId = state.showingBackWarningDialogText,
            quit = quit
        )
    }

    Column {
        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.padding(horizontal = LocalDimensions.current.mediumSpacing)) {
            Text(stringResource(R.string.notificationsMessage), style = LocalType.current.h4)
            Spacer(Modifier.height(LocalDimensions.current.smallSpacing))
            Text(
                Phrase.from(stringResource(R.string.onboardingMessageNotificationExplanation))
                    .put(APP_NAME_KEY, stringResource(R.string.app_name))
                    .format().toString(),
                style = LocalType.current.base
            )
            Spacer(Modifier.height(LocalDimensions.current.spacing))
        }

        NotificationRadioButton(
            R.string.notificationsFastMode,
            R.string.notificationsFastModeDescription,
            modifier = Modifier.contentDescription(R.string.AccessibilityId_notificationsFastMode),
            tag = R.string.recommended,
            checked = state.pushEnabled,
            onClick = { setEnabled(true) }
        )

        // spacing between buttons is provided by ripple/downstate of NotificationRadioButton

        val explanationTxt = Phrase.from(stringResource(R.string.notificationsSlowModeDescription))
            .put(APP_NAME_KEY, stringResource(R.string.app_name))
            .format().toString()

        NotificationRadioButton(
            stringResource(R.string.notificationsSlowMode),
            explanationTxt,
            modifier = Modifier.contentDescription(R.string.AccessibilityId_notificationsSlowMode),
            checked = state.pushDisabled,
            onClick = { setEnabled(false) }
        )

        Spacer(Modifier.weight(1f))

        ContinuePrimaryOutlineButton(Modifier.align(Alignment.CenterHorizontally), onContinue)
    }
}

@Composable
private fun NotificationRadioButton(
    @StringRes titleId: Int,
    @StringRes explanationId: Int,
    modifier: Modifier = Modifier,
    @StringRes tag: Int? = null,
    checked: Boolean = false,
    onClick: () -> Unit = {}
) {
    // Pass-through from this string ID version to the version that takes strings
    NotificationRadioButton(
        titleTxt       = stringResource(titleId),
        explanationTxt = stringResource(explanationId),
        modifier       = modifier,
        tag            = tag,
        checked        = checked,
        onClick        = onClick
    )
}

@Composable
private fun NotificationRadioButton(
    titleTxt: String,
    explanationTxt: String,
    modifier: Modifier = Modifier,
    @StringRes tag: Int? = null,
    checked: Boolean = false,
    onClick: () -> Unit = {}
) {
    RadioButton(
        onClick = onClick,
        modifier = modifier,
        selected = checked,
        contentPadding = PaddingValues(horizontal = LocalDimensions.current.mediumSpacing, vertical = 7.dp)
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
            Column(
                modifier = Modifier.padding(horizontal = LocalDimensions.current.smallSpacing, vertical = LocalDimensions.current.xsSpacing)) {
                Text(
                    titleTxt,
                    style = LocalType.current.h8
                )

                Text(
                    explanationTxt,
                    style = LocalType.current.small,
                    modifier = Modifier.padding(top = LocalDimensions.current.xxsSpacing)
                )

                tag?.let {
                    Text(
                        stringResource(it),
                        modifier = Modifier.padding(top = LocalDimensions.current.xxsSpacing),
                        color = LocalColors.current.primary,
                        style = LocalType.current.h9
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun MessageNotificationsScreenPreview(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        MessageNotificationsScreen()
    }
}
