package org.thoughtcrime.securesms.onboarding.landing

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.squareup.phrase.Phrase
import kotlinx.coroutines.delay
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.EMOJI_KEY
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.components.BorderlessHtmlButton
import org.thoughtcrime.securesms.ui.components.PrimaryFillButton
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineButton
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.theme.ThemeColors
import kotlin.time.Duration.Companion.milliseconds

@Preview
@Composable
private fun PreviewLandingScreen(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        LandingScreen({}, {}, {}, {})
    }
}

@Composable
internal fun LandingScreen(
    createAccount: () -> Unit,
    loadAccount: () -> Unit,
    openTerms: () -> Unit,
    openPrivacyPolicy: () -> Unit,
) {
    var count by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()

    var isUrlDialogVisible by remember { mutableStateOf(false) }

    if (isUrlDialogVisible) {
        AlertDialog(
            onDismissRequest = { isUrlDialogVisible = false },
            title = stringResource(R.string.urlOpen),
            text = stringResource(R.string.urlOpenBrowser),
            showCloseButton = true, // display the 'x' button
            buttons = listOf(
                DialogButtonModel(
                    text = GetString(R.string.onboardingTos),
                    onClick = openTerms
                ),
                DialogButtonModel(
                    text = GetString(R.string.onboardingPrivacy),
                    onClick = openPrivacyPolicy
                )
            )
        )
    }

    LaunchedEffect(Unit) {
        delay(500.milliseconds)
        while(count < MESSAGES.size) {
            count += 1
            listState.animateScrollToItem(0.coerceAtLeast((count - 1)))
            delay(1500L)
        }
    }

    Column {
        Column(modifier = Modifier
            .weight(1f)
            .padding(horizontal = LocalDimensions.current.mediumSpacing)
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.onboardingBubblePrivacyInYourPocket),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = LocalType.current.h4,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(LocalDimensions.current.spacing))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .heightIn(min = 200.dp)
                    .fillMaxWidth()
                    .weight(3f),
                verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallSpacing)
            ) {
                items(
                    MESSAGES.take(count),
                    key = { it.stringId }
                ) { item ->
                    // Perform string substitution only in the bubbles that require it
                    val bubbleTxt = when (item.stringId) {
                        R.string.onboardingBubbleWelcomeToSession -> {
                            Phrase.from(stringResource(item.stringId))
                                .put(APP_NAME_KEY, stringResource(R.string.app_name))
                                .put(EMOJI_KEY, "\uD83D\uDC4B") // this hardcoded emoji might be moved to NonTranslatableConstants eventually
                                .format().toString()
                        }
                        R.string.onboardingBubbleSessionIsEngineered -> {
                            Phrase.from(stringResource(item.stringId))
                                .put(APP_NAME_KEY, stringResource(R.string.app_name))
                                .format().toString()
                        }
                        R.string.onboardingBubbleCreatingAnAccountIsEasy -> {
                            Phrase.from(stringResource(item.stringId))
                                .put(EMOJI_KEY, "\uD83D\uDC47") // this hardcoded emoji might be moved to NonTranslatableConstants eventually
                                .format().toString()
                        }
                        else -> {
                            stringResource(item.stringId)
                        }
                    }

                    AnimateMessageText(
                        bubbleTxt,
                        item.isOutgoing
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        Column(modifier = Modifier.padding(horizontal = LocalDimensions.current.xlargeSpacing)) {
            PrimaryFillButton(
                text = stringResource(R.string.onboardingAccountCreate),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .contentDescription(R.string.AccessibilityId_onboardingAccountCreate),
                onClick = createAccount
            )
            Spacer(modifier = Modifier.height(LocalDimensions.current.smallSpacing))
            PrimaryOutlineButton(
                stringResource(R.string.onboardingAccountExists),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .contentDescription(R.string.AccessibilityId_onboardingAccountExists),
                onClick = loadAccount
            )
            BorderlessHtmlButton(
                textId = R.string.onboardingTosPrivacy,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .contentDescription(R.string.AccessibilityId_urlOpenBrowser),
                onClick = { isUrlDialogVisible = true }
            )
            Spacer(modifier = Modifier.height(LocalDimensions.current.xxsSpacing))
        }
    }
}

@Composable
private fun AnimateMessageText(text: String, isOutgoing: Boolean, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box {
        // TODO [SES-2077] Use LazyList itemAnimation when we update to compose 1.7 or so.
        MessageText(text, isOutgoing, Modifier.alpha(0f))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(durationMillis = 300)) +
                    slideInVertically(animationSpec = tween(durationMillis = 300)) { it }
        ) {
            MessageText(text, isOutgoing, modifier)
        }
    }
}

@Composable
private fun MessageText(text: String, isOutgoing: Boolean, modifier: Modifier) {
    Box(modifier = modifier then Modifier.fillMaxWidth()) {
        MessageText(
            text,
            color = if (isOutgoing) LocalColors.current.primary else LocalColors.current.backgroundBubbleReceived,
            textColor = if (isOutgoing) LocalColors.current.textBubbleSent else LocalColors.current.textBubbleReceived,
            modifier = Modifier.align(if (isOutgoing) Alignment.TopEnd else Alignment.TopStart)
        )
    }
}

@Composable
private fun MessageText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified
) {
    Box(
        modifier = modifier.fillMaxWidth(0.666f)
            .background(color = color, shape = MaterialTheme.shapes.small)
    ) {
        Text(
            text,
            style = LocalType.current.large,
            color = textColor,
            modifier = Modifier.padding(
                horizontal = LocalDimensions.current.smallSpacing,
                vertical = LocalDimensions.current.xsSpacing
            )
        )
    }
}

private data class TextData(
    @StringRes val stringId: Int,
    val isOutgoing: Boolean = false
)

private val MESSAGES = listOf(
    TextData(R.string.onboardingBubbleWelcomeToSession),
    TextData(R.string.onboardingBubbleSessionIsEngineered, isOutgoing = true),
    TextData(R.string.onboardingBubbleNoPhoneNumber),
    TextData(R.string.onboardingBubbleCreatingAnAccountIsEasy, isOutgoing = true)
)
