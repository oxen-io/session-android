package org.thoughtcrime.securesms.onboarding.recoverypassword

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.CellWithPaddingAndMargin
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.LaunchedEffectAsync
import org.thoughtcrime.securesms.ui.LocalExtraColors
import org.thoughtcrime.securesms.ui.OutlineButton
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionShieldIcon
import org.thoughtcrime.securesms.ui.ThemeResPreviewParameterProvider
import org.thoughtcrime.securesms.ui.classicDarkColors
import org.thoughtcrime.securesms.ui.colorDestructive
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.h8
import org.thoughtcrime.securesms.ui.small
import kotlin.time.Duration.Companion.seconds

class RecoveryPasswordActivity : BaseActionBarActivity() {

    private val viewModel: RecoveryPasswordViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.title = resources.getString(R.string.sessionRecoveryPassword)

        ComposeView(this).apply {
            setContent {
                RecoveryPassword(viewModel.seed, viewModel.qrBitmap, { viewModel.copySeed(context) }) { onHide() }
            }
        }.let(::setContentView)
    }

    private fun onHide() {
        showSessionDialog {
            title(R.string.recoveryPasswordHidePermanently)
            htmlText(R.string.recoveryPasswordHidePermanentlyDescription1)
            destructiveButton(R.string.continue_2) { onHideConfirm() }
            cancelButton()
        }
    }

    private fun onHideConfirm() {
        showSessionDialog {
            title(R.string.recoveryPasswordHidePermanently)
            text(R.string.recoveryPasswordHidePermanentlyDescription2)
            cancelButton()
            destructiveButton(
                R.string.yes,
                contentDescription = R.string.AccessibilityId_confirm_button
            ) {
                viewModel.permanentlyHidePassword()
                finish()
            }
        }
    }
}

@Preview
@Composable
fun PreviewRecoveryPassword(
    @PreviewParameter(ThemeResPreviewParameterProvider::class) themeResId: Int
) {
    PreviewTheme(themeResId) {
        RecoveryPassword(seed = "Voyage  urban  toyed  maverick peculiar tuxedo penguin tree grass building listen speak withdraw terminal plane")
    }
}

@Composable
fun RecoveryPassword(
    seed: String = "",
    qrBitmap: Bitmap? = null,
    copySeed:() -> Unit = {},
    onHide:() -> Unit = {}
) {
    AppTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            RecoveryPasswordCell(seed, qrBitmap, copySeed)
            HideRecoveryPasswordCell(onHide)
        }
    }
}

@Composable
fun RecoveryPasswordCell(seed: String = "", qrBitmap: Bitmap? = null, copySeed:() -> Unit = {}) {
    val showQr = remember {
        mutableStateOf(false)
    }

    CellWithPaddingAndMargin {
        Column {
            Row {
                Text(stringResource(R.string.sessionRecoveryPassword))
                Spacer(Modifier.width(8.dp))
                SessionShieldIcon()
            }

            Text(stringResource(R.string.recoveryPasswordDescription))

            AnimatedVisibility(!showQr.value) {
                Text(
                    seed,
                    modifier = Modifier
                        .contentDescription(R.string.AccessibilityId_hide_recovery_password_button)
                        .padding(vertical = 24.dp)
                        .border(
                            width = 1.dp,
                            color = classicDarkColors[3],
                            shape = RoundedCornerShape(11.dp)
                        )
                        .padding(24.dp),
                    style = MaterialTheme.typography.small.copy(fontFamily = FontFamily.Monospace),
                    color = LocalExtraColors.current.prominentButtonColor,
                )
            }

            AnimatedVisibility(showQr.value, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Card(
                    backgroundColor = LocalExtraColors.current.lightCell,
                    elevation = 0.dp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 24.dp)
                ) {
                    Box {
                        qrBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "QR code of your recovery password",
                                colorFilter = ColorFilter.tint(LocalExtraColors.current.onLightCell)
                            )
                        }

                        Icon(
                            painter = painterResource(id = R.drawable.session_shield),
                            contentDescription = "",
                            tint = LocalExtraColors.current.onLightCell,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .width(46.dp)
                                .height(56.dp)
                                .background(color = LocalExtraColors.current.lightCell)
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(!showQr.value) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    OutlineButton(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colors.onPrimary,
                        onClick = copySeed,
                        temporaryContent = { Text(stringResource(R.string.copied)) }
                    ) {
                        Text(stringResource(R.string.copy))
                    }
                    OutlineButton(text = stringResource(R.string.qrView), modifier = Modifier.weight(1f), color = MaterialTheme.colors.onPrimary) { showQr.toggle() }
                }
            }

            AnimatedVisibility(showQr.value, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                OutlineButton(
                    text = stringResource(R.string.recoveryPasswordView),
                    color = MaterialTheme.colors.onPrimary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { showQr.toggle() }
            }
        }
    }
}

private fun MutableState<Boolean>.toggle() { value = !value }

@Composable
fun HideRecoveryPasswordCell(onHide: () -> Unit = {}) {
    CellWithPaddingAndMargin {
        Row {
            Column(Modifier.weight(1f)) {
                Text(text = stringResource(R.string.recoveryPasswordHideRecoveryPassword), style = MaterialTheme.typography.h8)
                Text(text = stringResource(R.string.recoveryPasswordHideRecoveryPasswordDescription))
            }
            OutlineButton(
                stringResource(R.string.hide),
                contentDescription = GetString(R.string.AccessibilityId_hide_recovery_password_button),
                modifier = Modifier.align(Alignment.CenterVertically),
                color = colorDestructive
            ) { onHide() }
        }
    }
}

fun Context.startRecoveryPasswordActivity() {
    Intent(this, RecoveryPasswordActivity::class.java).also(::startActivity)
}
