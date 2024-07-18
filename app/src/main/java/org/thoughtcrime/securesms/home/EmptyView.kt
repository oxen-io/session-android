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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.theme.ThemeColors
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalType

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
                stringResource(R.string.welcome_to_session),
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
