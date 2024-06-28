package org.thoughtcrime.securesms.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.session_accent
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo

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
        Column(modifier = Modifier.padding(horizontal = 36.dp)) {
            Spacer(modifier = Modifier.weight(1f))
            Text(stringResource(R.string.onboardingBubblePrivacyInYourPocket), modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.h4, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            IncomingText(stringResource(R.string.onboardingBubbleWelcomeToSession))
            Spacer(modifier = Modifier.height(14.dp))
            OutgoingText(stringResource(R.string.onboardingBubbleSessionIsEngineered))
            Spacer(modifier = Modifier.height(14.dp))
            IncomingText(stringResource(R.string.onboardingBubbleNoPhoneNumber))
            Spacer(modifier = Modifier.height(14.dp))
            OutgoingText(stringResource(R.string.onboardingBubbleCreatingAnAccountIsEasy))
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
    private fun IncomingText(text: String) {
        ChatText(
            text,
            color = classicDarkColors[2]
        )
    }

    @Composable
    private fun ColumnScope.OutgoingText(text: String) {
        ChatText(
            text,
            color = session_accent,
            textColor = MaterialTheme.colors.primary,
            modifier = Modifier.align(Alignment.End)
        )
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
