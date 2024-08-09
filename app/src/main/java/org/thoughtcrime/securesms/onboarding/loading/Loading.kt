package org.thoughtcrime.securesms.onboarding.loading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.ProgressArc
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.theme.LocalType

@Composable
internal fun LoadingScreen(progress: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.weight(1f))
        ProgressArc(
            progress,
            modifier = Modifier.contentDescription(R.string.AccessibilityId_loadAccountProgressMessage)
        )
        Text(
            stringResource(R.string.waitOneMoment),
            style = LocalType.current.h7
        )
        Spacer(modifier = Modifier.height(LocalDimensions.current.xxxsSpacing))
        Text(
            stringResource(R.string.loadAccountProgressMessage),
            style = LocalType.current.base
        )
        Spacer(modifier = Modifier.weight(2f))
    }
}
