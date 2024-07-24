package org.thoughtcrime.securesms.onboarding.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineButton
import org.thoughtcrime.securesms.ui.contentDescription

@Composable
fun ContinuePrimaryOutlineButton(modifier: Modifier, onContinue: () -> Unit) {
    PrimaryOutlineButton(
        stringResource(R.string.continue_2),
        modifier = modifier
            .contentDescription(R.string.AccessibilityId_continue)
            .fillMaxWidth()
            .padding(horizontal = LocalDimensions.current.xlargeSpacing)
            .padding(bottom = LocalDimensions.current.smallSpacing),
        onClick = onContinue,
    )
}
