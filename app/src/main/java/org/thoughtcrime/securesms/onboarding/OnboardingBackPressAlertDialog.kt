package org.thoughtcrime.securesms.onboarding

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.theme.LocalColors

@Composable
fun OnboardingBackPressAlertDialog(
    dismissDialog: () -> Unit,
    @StringRes textId: Int = R.string.you_cannot_go_back_further_in_order_to_stop_loading_your_account_session_needs_to_quit,
    quit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = dismissDialog,
        title = stringResource(R.string.warning),
        text = stringResource(textId),
        buttons = listOf(
            DialogButtonModel(
                GetString(stringResource(R.string.quit)),
                color = LocalColors.current.danger,
                onClick = quit
            ),
            DialogButtonModel(
                GetString(stringResource(R.string.cancel))
            )
        )
    )
}
