package org.thoughtcrime.securesms.onboarding.loadaccount

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.flow.Flow
import network.loki.messenger.R
import org.thoughtcrime.securesms.onboarding.ui.ContinuePrimaryOutlineButton
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.components.MaybeScanQrCode
import org.thoughtcrime.securesms.ui.components.SessionOutlinedTextField
import org.thoughtcrime.securesms.ui.components.SessionTabRow
import org.thoughtcrime.securesms.ui.theme.LocalType

private val TITLES = listOf(R.string.sessionRecoveryPassword, R.string.qrScan)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LoadAccountScreen(
    state: State,
    qrErrors: Flow<String>,
    onChange: (String) -> Unit = {},
    onContinue: () -> Unit = {},
    onScan: (String) -> Unit = {}
) {
    val pagerState = rememberPagerState { TITLES.size }

    Column {
        SessionTabRow(pagerState, TITLES)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (TITLES[page]) {
                R.string.sessionRecoveryPassword -> RecoveryPassword(state, onChange, onContinue)
                R.string.qrScan -> MaybeScanQrCode(qrErrors, onScan = onScan)
            }
        }
    }
}

@Preview
@Composable
private fun PreviewRecoveryPassword() {
    PreviewTheme {
        RecoveryPassword(state = State())
    }
}

@Composable
private fun RecoveryPassword(state: State, onChange: (String) -> Unit = {}, onContinue: () -> Unit = {}) {
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
            Row {
                Text(
                    text = stringResource(R.string.sessionRecoveryPassword),
                    style = LocalType.current.h4
                )
                Spacer(Modifier.width(LocalDimensions.current.xxsSpacing))
                Icon(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    painter = painterResource(id = R.drawable.ic_shield_outline),
                    contentDescription = null,
                )
            }
            Spacer(Modifier.height(LocalDimensions.current.smallSpacing))
            Text(
                stringResource(R.string.activity_link_enter_your_recovery_password_to_load_your_account_if_you_haven_t_saved_it_you_can_find_it_in_your_app_settings),
                style = LocalType.current.base
            )
            Spacer(Modifier.height(LocalDimensions.current.spacing))
            SessionOutlinedTextField(
                text = state.recoveryPhrase,
                modifier = Modifier.fillMaxWidth(),
                contentDescription = stringResource(R.string.AccessibilityId_recovery_phrase_input),
                placeholder = stringResource(R.string.recoveryPasswordEnter),
                onChange = onChange,
                onContinue = onContinue,
                error = state.error,
                isTextErrorColor = state.isTextErrorColor
            )
        }

        Spacer(modifier = Modifier.height(LocalDimensions.current.smallSpacing))
        Spacer(Modifier.weight(2f))

        ContinuePrimaryOutlineButton(modifier = Modifier.align(Alignment.CenterHorizontally), onContinue)
    }
}
