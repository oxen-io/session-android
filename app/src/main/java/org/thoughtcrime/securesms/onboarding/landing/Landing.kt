package org.thoughtcrime.securesms.onboarding.landing

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import kotlinx.coroutines.delay
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.components.BorderlessHtmlButton
import org.thoughtcrime.securesms.ui.components.PrimaryFillButton
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineButton
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.h4
import org.thoughtcrime.securesms.ui.large
import kotlin.time.Duration.Companion.milliseconds

@Preview
@Composable
private fun PreviewLandingScreen(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: Colors
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
            buttons = listOf(
                DialogButtonModel(
                    text = GetString(R.string.activity_landing_terms_of_service),
                    contentDescription = GetString(R.string.AccessibilityId_terms_of_service_button),
                    onClick = openTerms
                ),
                DialogButtonModel(
                    text = GetString(R.string.activity_landing_privacy_policy),
                    contentDescription = GetString(R.string.AccessibilityId_privacy_policy_button),
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
            .padding(horizontal = LocalDimensions.current.onboardingMargin)
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.onboardingBubblePrivacyInYourPocket),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = h4,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(LocalDimensions.current.itemSpacing))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .heightIn(min = LocalDimensions.current.minScrollableViewHeight)
                    .fillMaxWidth()
                    .weight(3f),
                verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallItemSpacing)
            ) {
                items(
                    MESSAGES.take(count),
                    key = { it.stringId }
                ) { item ->
                    AnimateMessageText(
                        stringResource(item.stringId),
                        item.isOutgoing
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        Column(modifier = Modifier.padding(horizontal = LocalDimensions.current.largeMargin)) {
            PrimaryFillButton(
                text = stringResource(R.string.onboardingAccountCreate),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .contentDescription(R.string.AccessibilityId_create_account_button),
                onClick = createAccount
            )
            Spacer(modifier = Modifier.height(LocalDimensions.current.smallItemSpacing))
            PrimaryOutlineButton(
                stringResource(R.string.onboardingAccountExists),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .contentDescription(R.string.AccessibilityId_restore_account_button),
                onClick = loadAccount
            )
            BorderlessHtmlButton(
                textId = R.string.onboardingTosPrivacy,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .contentDescription(R.string.AccessibilityId_open_url),
                onClick = { isUrlDialogVisible = true }
            )
            Spacer(modifier = Modifier.height(LocalDimensions.current.xxsItemSpacing))
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
            color = if (isOutgoing) LocalColors.current.backgroundBubbleSent else LocalColors.current.backgroundBubbleReceived,
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
    Card(
        modifier = modifier.fillMaxWidth(0.666f),
        shape = MaterialTheme.shapes.small,
        backgroundColor = color,
        elevation = 0.dp
    ) {
        Text(
            text,
            style = large,
            color = textColor,
            modifier = Modifier.padding(
                horizontal = LocalDimensions.current.smallItemSpacing,
                vertical = LocalDimensions.current.xsItemSpacing
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
