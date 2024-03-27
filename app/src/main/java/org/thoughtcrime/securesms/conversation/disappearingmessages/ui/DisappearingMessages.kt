package org.thoughtcrime.securesms.conversation.disappearingmessages.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.thoughtcrime.securesms.ui.Callbacks
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.NoOpCallbacks
import org.thoughtcrime.securesms.ui.OptionsCard
import org.thoughtcrime.securesms.ui.OutlineButton
import org.thoughtcrime.securesms.ui.RadioOption
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.fadingEdges

typealias ExpiryCallbacks = Callbacks<ExpiryMode>
typealias ExpiryRadioOption = RadioOption<ExpiryMode>

@Composable
fun DisappearingMessages(
    state: UiState,
    modifier: Modifier = Modifier,
    callbacks: ExpiryCallbacks = NoOpCallbacks
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier.padding(horizontal = 32.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .verticalScroll(scrollState)
                    .fadingEdges(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.cards.forEach {
                    OptionsCard(it, callbacks)
                }

                if (state.showGroupFooter) Text(text = stringResource(R.string.activity_disappearing_messages_group_footer),
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight(400),
                        color = Color(0xFFA1A2A1),
                        textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth())
            }
        }

        if (state.showSetButton) OutlineButton(
            GetString(R.string.disappearing_messages_set_button_title),
            modifier = Modifier
                .contentDescription(GetString(R.string.AccessibilityId_set_button))
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 20.dp),
            onClick = callbacks::onSetClick
        )
    }
}
