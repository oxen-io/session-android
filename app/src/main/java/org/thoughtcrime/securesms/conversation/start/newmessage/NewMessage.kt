package org.thoughtcrime.securesms.conversation.start.newmessage

import android.graphics.Rect
import android.os.Build
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import network.loki.messenger.R
import org.thoughtcrime.securesms.conversation.start.StartConversationFragment.Companion.PEEK_RATIO
import org.thoughtcrime.securesms.ui.LoadingArcOr
import org.thoughtcrime.securesms.ui.components.AppBarCloseIcon
import org.thoughtcrime.securesms.ui.components.BackAppBar
import org.thoughtcrime.securesms.ui.components.BorderlessButtonWithIcon
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineButton
import org.thoughtcrime.securesms.ui.components.QRScannerScreen
import org.thoughtcrime.securesms.ui.components.SessionOutlinedTextField
import org.thoughtcrime.securesms.ui.components.SessionTabRow
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.qaTag
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.theme.ThemeColors
import kotlin.math.max

private val TITLES = listOf(R.string.accountIdEnter, R.string.qrScan)

@OptIn(ExperimentalMaterial3Api::class)
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
        // `messageNew` is now a plurals string so get the singular version
        val context = LocalContext.current
        val newMessageTitleTxt:String = context.resources.getQuantityString(R.plurals.messageNew, 1, 1)

        BackAppBar(
            title = newMessageTitleTxt,
            backgroundColor = Color.Transparent, // transparent to show the rounded shape of the container
            onBack = onBack,
            actions = { AppBarCloseIcon(onClose = onClose) }
        )
        SessionTabRow(pagerState, TITLES)
        HorizontalPager(pagerState) {
            when (TITLES[it]) {
                R.string.accountIdEnter -> EnterAccountId(state, callbacks, onHelp)
                R.string.qrScan -> QRScannerScreen(qrErrors, onScan = callbacks::onScanQrCode)
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
    // the scaffold is required to provide the contentPadding. That contentPadding is needed
    // to properly handle the ime padding.
    Scaffold() { contentPadding ->
        // we need this extra surface to handle nested scrolling properly,
        // because this scrollable component is inside a bottomSheet dialog which is itself scrollable
        Surface(
            modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
            color = LocalColors.current.backgroundSecondary
        ) {

            var accountModifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())

            // There is a known issue with the ime padding on android versions below 30
            // So on these older versions we need to resort to some manual padding based on the visible height
            // when the keyboard is up
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val keyboardHeight by keyboardHeight()
                accountModifier = accountModifier.padding(bottom = keyboardHeight)
            } else {
                accountModifier = accountModifier
                    .consumeWindowInsets(contentPadding)
                    .imePadding()
            }

            Column(
                modifier = accountModifier
            ) {
                Column(
                    modifier = Modifier.padding(vertical = LocalDimensions.current.spacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SessionOutlinedTextField(
                        text = state.newMessageIdOrOns,
                        modifier = Modifier
                            .padding(horizontal = LocalDimensions.current.spacing)
                            .qaTag(stringResource(R.string.AccessibilityId_sessionIdInput)),
                        placeholder = stringResource(R.string.accountIdOrOnsEnter),
                        onChange = callbacks::onChange,
                        onContinue = callbacks::onContinue,
                        error = state.error?.string(),
                        isTextErrorColor = state.isTextErrorColor
                    )

                    Spacer(modifier = Modifier.height(LocalDimensions.current.xxxsSpacing))

                    BorderlessButtonWithIcon(
                        text = stringResource(R.string.messageNewDescriptionMobile),
                        modifier = Modifier
                            .contentDescription(R.string.AccessibilityId_messageNewDescriptionMobile)
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
    }
}

@Composable
fun keyboardHeight(): MutableState<Dp> {
    val view = LocalView.current
    var keyboardHeight = remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height * PEEK_RATIO
            val keypadHeightPx = max( screenHeight - rect.bottom, 0f)

            keyboardHeight.value = with(density) { keypadHeightPx.toDp() }
        }

        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    return keyboardHeight
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
