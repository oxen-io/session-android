package org.thoughtcrime.securesms.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.h4
import org.thoughtcrime.securesms.ui.h8
import org.thoughtcrime.securesms.ui.small

@Composable
internal fun EmptyView(newAccount: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = LocalDimensions.current.homeEmptyViewMargin)
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
                style = h4,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.welcome_to_session),
                style = base,
                color = LocalColors.current.primary,
                textAlign = TextAlign.Center
            )
        }

        Divider(modifier = Modifier.padding(vertical = LocalDimensions.current.xsMargin))

        Text(
            stringResource(R.string.conversationsNone),
            style = h8,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = LocalDimensions.current.xsItemSpacing))
        Text(
            stringResource(R.string.onboardingHitThePlusButton),
            style = small,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.weight(2f))
    }
}

@Preview
@Composable
fun PreviewEmptyView(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: Colors
) {
    PreviewTheme(colors) {
        EmptyView(newAccount = false)
    }
}

@Preview
@Composable
fun PreviewEmptyViewNew(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: Colors
) {
    PreviewTheme(colors) {
        EmptyView(newAccount = true)
    }
}
