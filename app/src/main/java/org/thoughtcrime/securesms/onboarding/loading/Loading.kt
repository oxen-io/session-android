package org.thoughtcrime.securesms.onboarding.loading

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
internal fun LoadingScreen(state: State) {
    val animatable = remember { Animatable(initialValue = 0f, visibilityThreshold = 0.005f) }

    LaunchedEffect(state) {
        animatable.stop()
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = TweenSpec(durationMillis = state.duration.inWholeMilliseconds.toInt())
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.weight(1f))
        ProgressArc(
            animatable.value,
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
