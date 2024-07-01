package org.thoughtcrime.securesms.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.onboarding.pickname.startPickDisplayNameActivity
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.BorderlessButton
import org.thoughtcrime.securesms.ui.FilledButton
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.OutlineButton
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.ThemeResPreviewParameterProvider
import org.thoughtcrime.securesms.ui.classicDarkColors
import org.thoughtcrime.securesms.ui.session_accent
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo
import kotlin.time.Duration.Companion.milliseconds

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

class LandingActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // We always hit this LandingActivity on launch - but if there is a previous instance of
        // Session then close this activity to resume the last activity from the previous instance.
        if (!isTaskRoot) { finish(); return }

        setUpActionBarSessionLogo(true)

        ComposeView(this)
            .apply { setContent { AppTheme { LandingScreen() } } }
            .let(::setContentView)

        IdentityKeyUtil.generateIdentityKeyPair(this)
        TextSecurePreferences.setPasswordDisabled(this, true)
        // AC: This is a temporary workaround to trick the old code that the screen is unlocked.
        KeyCachingService.setMasterSecret(applicationContext, Object())
    }

    @Preview
    @Composable
    private fun LandingScreen(
        @PreviewParameter(ThemeResPreviewParameterProvider::class) themeResId: Int
    ) {
        PreviewTheme(themeResId) {
            LandingScreen()
        }
    }

    @Composable
    private fun LandingScreen() {

        var count by remember { mutableStateOf(0) }
        val listState = rememberLazyListState()

        LaunchedEffect(Unit) {
            delay(500.milliseconds)
            while(count < MESSAGES.size) {
                count += 1
                listState.animateScrollToItem(0.coerceAtLeast((count - 1)))
                delay(1500L)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 36.dp)) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.onboardingBubblePrivacyInYourPocket),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.h4,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .heightIn(min = 200.dp)
                    .fillMaxWidth()
                    .weight(2f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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

            OutlineButton(
                text = stringResource(R.string.onboardingAccountCreate),
                modifier = Modifier
                    .width(262.dp)
                    .align(Alignment.CenterHorizontally),
                contentDescription = GetString(R.string.AccessibilityId_create_account_button)
            ) { startPickDisplayNameActivity() }
            Spacer(modifier = Modifier.height(14.dp))
            FilledButton(
                text = stringResource(R.string.onboardingAccountExists),
                modifier = Modifier
                    .width(262.dp)
                    .align(Alignment.CenterHorizontally),
                contentDescription = GetString(R.string.AccessibilityId_restore_account_button)
            ) { startLinkDeviceActivity() }
            Spacer(modifier = Modifier.height(8.dp))
            BorderlessButton(
                text = stringResource(R.string.onboardingTosPrivacy),
                modifier = Modifier
                    .width(262.dp)
                    .align(Alignment.CenterHorizontally),
                contentDescription = GetString(R.string.AccessibilityId_open_url),
                fontSize = 11.sp,
                lineHeight = 13.sp
            ) { openDialog() }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    private fun openDialog() {
        showSessionDialog {
            title(R.string.urlOpen)
            text(R.string.urlOpenBrowser)
            button(
                R.string.activity_landing_terms_of_service,
                contentDescriptionRes = R.string.AccessibilityId_terms_of_service_link
            ) { open("https://getsession.org/terms-of-service") }
            button(
                R.string.activity_landing_privacy_policy,
                contentDescriptionRes = R.string.AccessibilityId_privacy_policy_link
            ) { open("https://getsession.org/privacy-policy") }
        }
    }

    private fun open(url: String) {
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).let(::startActivity)
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
        if (isOutgoing) OutgoingText(text, modifier) else IncomingText(text, modifier)
    }

    @Composable
    private fun IncomingText(text: String, modifier: Modifier = Modifier) {
        ChatText(
            text,
            color = classicDarkColors[2],
            modifier = modifier
        )
    }

    @Composable
    private fun OutgoingText(text: String, modifier: Modifier = Modifier) {
        Box(modifier = modifier then Modifier.fillMaxWidth()) {
            ChatText(
                text,
                color = session_accent,
                textColor = MaterialTheme.colors.primary,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }

    @Composable
    private fun ChatText(
        text: String,
        color: Color,
        modifier: Modifier = Modifier,
        textColor: Color = Color.Unspecified
    ) {
        Text(
            text,
            fontSize = 16.sp,
            lineHeight = 19.sp,
            color = textColor,
            modifier = modifier
                .fillMaxWidth(0.666f)
                .background(
                    color = color,
                    shape = RoundedCornerShape(size = 13.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
