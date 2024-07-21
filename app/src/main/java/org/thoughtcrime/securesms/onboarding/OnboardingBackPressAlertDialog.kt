package org.thoughtcrime.securesms.onboarding

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.color.LocalColors

@Composable
fun OnboardingBackPressAlertDialog(
    dismissDialog: () -> Unit,
    @StringRes textId: Int = R.string.onboardingBackAccountCreation,
    quit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = dismissDialog,
        title = stringResource(R.string.warning),
        text = stringResource(textId),
        buttons = listOf(
            DialogButtonModel(
                GetString(stringResource(R.string.quitButton)),
                color = LocalColors.current.danger,
                onClick = quit
            ),
            DialogButtonModel(
                GetString(stringResource(R.string.cancel))
            )
        )
    )
}
