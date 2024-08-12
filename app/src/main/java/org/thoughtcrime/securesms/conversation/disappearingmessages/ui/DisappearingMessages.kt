package org.thoughtcrime.securesms.conversation.disappearingmessages.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.thoughtcrime.securesms.ui.Callbacks
import org.thoughtcrime.securesms.ui.NoOpCallbacks
import org.thoughtcrime.securesms.ui.OptionsCard
import org.thoughtcrime.securesms.ui.RadioOption
import org.thoughtcrime.securesms.ui.components.SlimOutlineButton
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.fadingEdges
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType

typealias ExpiryCallbacks   = Callbacks<ExpiryMode>
typealias ExpiryRadioOption = RadioOption<ExpiryMode>

@Composable
fun DisappearingMessages(
    state: UiState,
    modifier: Modifier = Modifier,
    callbacks: ExpiryCallbacks = NoOpCallbacks
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier.padding(horizontal = LocalDimensions.current.spacing)) {
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .padding(vertical = LocalDimensions.current.spacing)
                    .verticalScroll(scrollState)
                    .fadingEdges(scrollState),
            ) {
                state.cards.forEachIndexed { index, option ->
                    OptionsCard(option, callbacks)

                    // add spacing if not the last item
                    if(index != state.cards.lastIndex){
                        Spacer(modifier = Modifier.height(LocalDimensions.current.spacing))
                    }
                }

                if (state.showGroupFooter) Text(
                    text = stringResource(R.string.disappearingMessagesDescription) +
                                          "\n"                                      +
                                          stringResource(R.string.disappearingMessagesOnlyAdmins),
                    style = LocalType.current.extraSmall,
                    fontWeight = FontWeight(400),
                    color = LocalColors.current.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = LocalDimensions.current.xsSpacing)
                )
            }
        }

        if (state.showSetButton) SlimOutlineButton(
            stringResource(R.string.set),
            modifier = Modifier
                .contentDescription(R.string.AccessibilityId_setButton)
                .align(Alignment.CenterHorizontally)
                .padding(bottom = LocalDimensions.current.spacing),
            onClick = callbacks::onSetClick
        )
    }
}
