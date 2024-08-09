package org.thoughtcrime.securesms.onboarding.pickname

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import network.loki.messenger.R
import org.thoughtcrime.securesms.onboarding.OnboardingBackPressAlertDialog
import org.thoughtcrime.securesms.onboarding.ui.ContinuePrimaryOutlineButton
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.components.SessionOutlinedTextField
import org.thoughtcrime.securesms.ui.theme.LocalType

@Preview
@Composable
private fun PreviewPickDisplayName() {
    PreviewTheme {
        PickDisplayName(State())
    }
}

@Composable
internal fun PickDisplayName(
    state: State,
    onChange: (String) -> Unit = {},
    onContinue: () -> Unit = {},
    dismissDialog: () -> Unit = {},
    quit: () -> Unit = {}
) {

    if (state.showDialog) OnboardingBackPressAlertDialog(
        dismissDialog,
        R.string.onboardingBackAccountCreation,
        quit
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.weight(1f))
        Spacer(modifier = Modifier.height(LocalDimensions.current.smallSpacing))

        Column(
            modifier = Modifier.padding(horizontal = LocalDimensions.current.mediumSpacing)
        ) {
            Text(stringResource(state.title), style = LocalType.current.h4)
            Spacer(Modifier.height(LocalDimensions.current.smallSpacing))
            Text(
                stringResource(state.description),
                style = LocalType.current.base,
                modifier = Modifier.padding(bottom = LocalDimensions.current.xsSpacing))
            Spacer(Modifier.height(LocalDimensions.current.spacing))
            SessionOutlinedTextField(
                text = state.displayName,
                modifier = Modifier.fillMaxWidth(),
                contentDescription = stringResource(R.string.AccessibilityId_displayNameEnter),
                placeholder = stringResource(R.string.displayNameEnter),
                onChange = onChange,
                onContinue = onContinue,
                error = state.error?.let { stringResource(it) },
                isTextErrorColor = state.isTextErrorColor
            )
        }

        Spacer(modifier = Modifier.height(LocalDimensions.current.smallSpacing))
        Spacer(Modifier.weight(2f))

        ContinuePrimaryOutlineButton(modifier = Modifier.align(Alignment.CenterHorizontally), onContinue)
    }
}
