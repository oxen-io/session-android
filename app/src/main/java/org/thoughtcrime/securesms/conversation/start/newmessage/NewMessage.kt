package org.thoughtcrime.securesms.conversation.start.newmessage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import network.loki.messenger.R
import org.thoughtcrime.securesms.onboarding.ui.ContinuePrimaryOutlineButton
import org.thoughtcrime.securesms.ui.LoadingArcOr
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.theme.ThemeColors
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.components.AppBar
import org.thoughtcrime.securesms.ui.components.BorderlessButtonWithIcon
import org.thoughtcrime.securesms.ui.components.MaybeScanQrCode
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineButton
import org.thoughtcrime.securesms.ui.components.SessionOutlinedTextField
import org.thoughtcrime.securesms.ui.components.SessionTabRow
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.theme.LocalType

private val TITLES = listOf(R.string.enter_account_id, R.string.qrScan)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NewMessage(
    state: State,
    qrErrors: Flow<String> = emptyFlow(),
    callbacks: Callbacks = object: Callbacks {},
    onClose: () -> Unit = {},
    onBack: () -> Unit = {},
    onHelp: () -> Unit = {},
) {
    val pagerState = rememberPagerState { TITLES.size }

    Column(modifier = Modifier.background(
        LocalColors.current.backgroundSecondary,
        shape = MaterialTheme.shapes.small
    )) {
        AppBar(stringResource(R.string.messageNew), onClose = onClose, onBack = onBack)
        SessionTabRow(pagerState, TITLES)
        HorizontalPager(pagerState) {
            when (TITLES[it]) {
                R.string.enter_account_id -> EnterAccountId(state, callbacks, onHelp)
                R.string.qrScan -> MaybeScanQrCode(qrErrors, onScan = callbacks::onScanQrCode)
            }
        }
    }
}

@Composable
private fun EnterAccountId(
    state: State,
    callbacks: Callbacks,
    onHelp: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
    ) {
        Column(
            modifier = Modifier.padding(vertical = LocalDimensions.current.spacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SessionOutlinedTextField(
                text = state.newMessageIdOrOns,
                modifier = Modifier
                    .padding(horizontal = LocalDimensions.current.spacing),
                contentDescription = "Session id input box",
                placeholder = stringResource(R.string.accountIdOrOnsEnter),
                onChange = callbacks::onChange,
                onContinue = callbacks::onContinue,
                error = state.error?.string(),
                isTextErrorColor = state.isTextErrorColor
            )

            Spacer(modifier = Modifier.height(LocalDimensions.current.xxxsSpacing))

            BorderlessButtonWithIcon(
                text = stringResource(R.string.messageNewDescription),
                modifier = Modifier
                    .contentDescription(R.string.AccessibilityId_help_desk_link)
                    .padding(horizontal = LocalDimensions.current.mediumSpacing)
                    .fillMaxWidth(),
                style = LocalType.current.small,
                color = LocalColors.current.textSecondary,
                iconRes = R.drawable.ic_circle_question_mark,
                onClick = onHelp
            )
        }

        Spacer(modifier = Modifier.height(LocalDimensions.current.smallSpacing))
        Spacer(Modifier.weight(2f))

        PrimaryOutlineButton(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = LocalDimensions.current.xlargeSpacing)
                .padding(bottom = LocalDimensions.current.smallSpacing)
                .fillMaxWidth()
                .contentDescription(R.string.next),
            enabled = state.isNextButtonEnabled,
            onClick = callbacks::onContinue
        ) {
            LoadingArcOr(state.loading) {
                Text(stringResource(R.string.next))
            }
        }
    }
}

@Preview
@Composable
private fun PreviewNewMessage(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        NewMessage(State("z"))
    }
}
