package org.thoughtcrime.securesms.onboarding.messagenotifications

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.thoughtcrime.securesms.onboarding.ui.ContinuePrimaryOutlineButton
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.color.transparentButtonColors
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.h4
import org.thoughtcrime.securesms.ui.h8
import org.thoughtcrime.securesms.ui.h9
import org.thoughtcrime.securesms.ui.small

@Composable
internal fun MessageNotificationsScreen(
    state: MessageNotificationsState = MessageNotificationsState(),
    setEnabled: (Boolean) -> Unit = {},
    onContinue: () -> Unit = {}
) {
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
            selected = state.pushEnabled,
            onClick = { setEnabled(true) }
        )

        // spacing between buttons is provided by ripple/downstate of NotificationRadioButton

        NotificationRadioButton(
            R.string.activity_pn_mode_slow_mode,
            R.string.activity_pn_mode_slow_mode_explanation,
            modifier = Modifier.contentDescription(R.string.AccessibilityId_slow_mode_notifications_button),
            selected = state.pushDisabled,
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
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    TextButton(
        modifier = modifier.fillMaxWidth(),
        colors = transparentButtonColors(),
        onClick = onClick,
        shape = RectangleShape,
        contentPadding = PaddingValues(horizontal = LocalDimensions.current.margin, vertical = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .border(LocalDimensions.current.borderStroke, LocalColors.current.borders, RoundedCornerShape(8.dp)),
        ) {
            Column(modifier = Modifier.padding(horizontal = 15.dp).padding(top = 10.dp, bottom = 11.dp)) {
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
        Spacer(modifier = Modifier.width(20.dp))
        RadioButtonIndicator(
            selected = selected,
            modifier = Modifier
                .size(22.dp)
                .align(Alignment.CenterVertically)
        )
    }
}

@Composable
private fun RadioButtonIndicator(
    selected: Boolean,
    modifier: Modifier
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            selected,
            modifier = Modifier.padding(2.5.dp).clip(CircleShape),
            enter = scaleIn(),
            exit = scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = LocalColors.current.primary,
                        shape = CircleShape
                    )
            )
        }
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .border(
                    width = LocalDimensions.current.borderStroke,
                    color = LocalColors.current.text,
                    shape = CircleShape
                )
        ) {}
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
