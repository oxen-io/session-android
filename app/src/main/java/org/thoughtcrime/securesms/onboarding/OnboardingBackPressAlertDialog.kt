package org.thoughtcrime.securesms.onboarding

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.theme.LocalColors

@Composable
fun OnboardingBackPressAlertDialog(
    dismissDialog: () -> Unit,
    @StringRes textId: Int = R.string.onboardingBackAccountCreation,
    quit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = dismissDialog,
        title = stringResource(R.string.warning),
        text = stringResource(textId).let { txt ->
            val c = LocalContext.current
            Phrase.from(txt).put(APP_NAME_KEY, c.getString(R.string.app_name)).format().toString()
        },
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
