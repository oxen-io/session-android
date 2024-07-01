package org.thoughtcrime.securesms.onboarding.pickname

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import network.loki.messenger.R
import org.thoughtcrime.securesms.onboarding.ui.ContinuePrimaryOutlineButton
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.components.SessionOutlinedTextField
import org.thoughtcrime.securesms.ui.h4

@Preview
@Composable
private fun PreviewDisplayName() {
    PreviewTheme {
        DisplayName(State())
    }
}

@Composable
internal fun DisplayName(state: State, onChange: (String) -> Unit = {}, onContinue: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.weight(1f))
        Spacer(modifier = Modifier.height(LocalDimensions.current.smallItemSpacing))

        Column(
            modifier = Modifier.padding(horizontal = LocalDimensions.current.largeMargin)
        ) {
            Text(stringResource(state.title), style = h4)
            Spacer(Modifier.height(LocalDimensions.current.smallItemSpacing))
            Text(
                stringResource(state.description),
                style = base,
                modifier = Modifier.padding(bottom = LocalDimensions.current.xsItemSpacing))
            Spacer(Modifier.height(LocalDimensions.current.itemSpacing))
            SessionOutlinedTextField(
                text = state.displayName,
                modifier = Modifier.fillMaxWidth(),
                contentDescription = stringResource(R.string.AccessibilityId_enter_display_name),
                placeholder = stringResource(R.string.displayNameEnter),
                onChange = onChange,
                onContinue = onContinue,
                error = state.error?.let { stringResource(it) },
                isTextErrorColor = state.isTextErrorColor
            )
        }

        Spacer(modifier = Modifier.height(LocalDimensions.current.smallItemSpacing))
        Spacer(Modifier.weight(2f))

        ContinuePrimaryOutlineButton(modifier = Modifier.align(Alignment.CenterHorizontally), onContinue)
    }
}
