package org.thoughtcrime.securesms.onboarding.loading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.ProgressArc
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.h7

@Composable
internal fun LoadingScreen(progress: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.weight(1f))
        ProgressArc(
            progress,
            modifier = Modifier.contentDescription(R.string.AccessibilityId_loading_animation)
        )
        Text(
            stringResource(R.string.waitOneMoment),
            style = h7
        )
        Spacer(modifier = Modifier.height(LocalDimensions.current.xxxsItemSpacing))
        Text(
            stringResource(R.string.loadAccountProgressMessage),
            style = base
        )
        Spacer(modifier = Modifier.weight(2f))
    }
}
