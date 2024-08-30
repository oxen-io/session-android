package org.thoughtcrime.securesms.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.NonTranslatableStringConstants.WAVING_HAND_EMOJI
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.EMOJI_KEY
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.theme.ThemeColors

@Composable
internal fun EmptyView(newAccount: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 50.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            painter = painterResource(id = if (newAccount) R.drawable.emoji_tada_large else R.drawable.ic_logo_large),
            contentDescription = null,
            tint = Color.Unspecified
        )
        if (newAccount) {
            Text(
                stringResource(R.string.onboardingAccountCreated),
                style = LocalType.current.h4,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.onboardingBubbleWelcomeToSession).let { txt ->
                    val c = LocalContext.current
                    Phrase.from(txt)
                        .put(APP_NAME_KEY, c.getString(R.string.app_name))
                        .put(EMOJI_KEY, WAVING_HAND_EMOJI)
                        .format().toString()
                },
                style = LocalType.current.base,
                color = LocalColors.current.primary,
                textAlign = TextAlign.Center
            )
        }

        Divider(modifier = Modifier.padding(vertical = LocalDimensions.current.smallSpacing))

        Text(
            stringResource(R.string.conversationsNone),
            style = LocalType.current.h8,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = LocalDimensions.current.xsSpacing))
        Text(
            stringResource(R.string.onboardingHitThePlusButton),
            style = LocalType.current.small,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.weight(2f))
    }
}

@Preview
@Composable
fun PreviewEmptyView(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        EmptyView(newAccount = false)
    }
}

@Preview
@Composable
fun PreviewEmptyViewNew(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        EmptyView(newAccount = true)
    }
}
