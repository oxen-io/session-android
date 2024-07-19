package org.thoughtcrime.securesms.recoverypassword

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.CellWithPaddingAndMargin
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.SessionShieldIcon
import org.thoughtcrime.securesms.ui.theme.ThemeColors
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.components.QrImage
import org.thoughtcrime.securesms.ui.components.SlimOutlineButton
import org.thoughtcrime.securesms.ui.components.SlimOutlineCopyButton
import org.thoughtcrime.securesms.ui.components.border
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.monospace

@Composable
internal fun RecoveryPasswordScreen(
    mnemonic: String,
    seed: String? = null,
    copyMnemonic:() -> Unit = {},
    onHide:() -> Unit = {}
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallSpacing),
        modifier = Modifier
            .contentDescription(R.string.AccessibilityId_recovery_password)
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalDimensions.current.smallSpacing)
    ) {
        RecoveryPasswordCell(mnemonic, seed, copyMnemonic)
        HideRecoveryPasswordCell(onHide)
    }
}

@Composable
private fun RecoveryPasswordCell(
    mnemonic: String,
    seed: String?,
    copyMnemonic:() -> Unit = {}
) {
    var showQr by remember {
        mutableStateOf(false)
    }

    CellWithPaddingAndMargin {
        Column {
            Row {
                Text(
                    stringResource(R.string.sessionRecoveryPassword),
                    style = LocalType.current.h8
                )
                Spacer(Modifier.width(LocalDimensions.current.xxsSpacing))
                SessionShieldIcon()
            }

            Spacer(modifier = Modifier.height(LocalDimensions.current.xxsSpacing))

            Text(
                stringResource(R.string.recoveryPasswordDescription),
                style = LocalType.current.base
            )

            AnimatedVisibility(!showQr) {
                RecoveryPassword(mnemonic)
            }

            AnimatedVisibility(
                showQr,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                QrImage(
                    seed,
                    modifier = Modifier
                        .padding(vertical = LocalDimensions.current.spacing)
                        .contentDescription(R.string.AccessibilityId_qr_code),
                    contentPadding = 10.dp,
                    icon = R.drawable.session_shield
                )
            }

            AnimatedVisibility(!showQr) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SlimOutlineCopyButton(
                        Modifier.weight(1f),
                        onClick = copyMnemonic
                    )
                    SlimOutlineButton(
                        stringResource(R.string.qrView),
                        Modifier.weight(1f),
                    ) { showQr = !showQr }
                }
            }

            AnimatedVisibility(showQr, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                SlimOutlineButton(
                    stringResource(R.string.recoveryPasswordView),
                    onClick = { showQr = !showQr }
                )
            }
        }
    }
}

@Composable
private fun RecoveryPassword(mnemonic: String) {
    Text(
        mnemonic,
        modifier = Modifier
            .contentDescription(R.string.AccessibilityId_recovery_password_container)
            .padding(vertical = LocalDimensions.current.spacing)
            .border()
            .padding(LocalDimensions.current.spacing),
        textAlign = TextAlign.Center,
        style = LocalType.current.extraSmall.monospace(),
        color = LocalColors.current.run { if (isLight) text else primary },
    )
}

@Composable
private fun HideRecoveryPasswordCell(onHide: () -> Unit = {}) {
    CellWithPaddingAndMargin {
        Row {
            Column(
                Modifier.weight(1f)
            ) {
                Text(
                    stringResource(R.string.recoveryPasswordHideRecoveryPassword),
                    style = LocalType.current.h8
                )
                Text(
                    stringResource(R.string.recoveryPasswordHideRecoveryPasswordDescription),
                    style = LocalType.current.base
                )
            }
            Spacer(modifier = Modifier.width(LocalDimensions.current.xsSpacing))
            SlimOutlineButton(
                text = stringResource(R.string.hide),
                modifier = Modifier
                    .wrapContentWidth()
                    .align(Alignment.CenterVertically)
                    .contentDescription(R.string.AccessibilityId_hide_recovery_password_button),
                color = LocalColors.current.danger,
                onClick = onHide
            )
        }
    }
}

@Preview
@Composable
private fun PreviewRecoveryPasswordScreen(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        RecoveryPasswordScreen(mnemonic = "voyage  urban  toyed  maverick peculiar tuxedo penguin tree grass building listen speak withdraw terminal plane")
    }
}
