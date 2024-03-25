package org.thoughtcrime.securesms.conversation.disappearingmessages.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.thoughtcrime.securesms.conversation.disappearingmessages.ExpiryType
import org.thoughtcrime.securesms.conversation.disappearingmessages.State
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.ThemeResPreviewParameterProvider

@Preview(widthDp = 450, heightDp = 700)
@Composable
fun PreviewStates(
    @PreviewParameter(StatePreviewParameterProvider::class) state: State
) {
    PreviewTheme(R.style.Classic_Dark) {
        DisappearingMessages(
            state.toUiState()
        )
    }
}

class StatePreviewParameterProvider : PreviewParameterProvider<State> {
    override val values = newConfigValues.filter { it.expiryType != ExpiryType.LEGACY } + newConfigValues.map { it.copy(isNewConfigEnabled = false) }

    private val newConfigValues get() = sequenceOf(
        // new 1-1
        State(expiryMode = ExpiryMode.NONE),
        State(expiryMode = ExpiryMode.Legacy(43200)),
        State(expiryMode = ExpiryMode.AfterRead(300)),
        State(expiryMode = ExpiryMode.AfterSend(43200)),
        // new group non-admin
        State(isGroup = true, isSelfAdmin = false),
        State(isGroup = true, isSelfAdmin = false, expiryMode = ExpiryMode.Legacy(43200)),
        State(isGroup = true, isSelfAdmin = false, expiryMode = ExpiryMode.AfterSend(43200)),
        // new group admin
        State(isGroup = true),
        State(isGroup = true, expiryMode = ExpiryMode.Legacy(43200)),
        State(isGroup = true, expiryMode = ExpiryMode.AfterSend(43200)),
        // new note-to-self
        State(isNoteToSelf = true),
    )
}

@Preview
@Composable
fun PreviewThemes(
    @PreviewParameter(ThemeResPreviewParameterProvider::class) themeResId: Int
) {
    PreviewTheme(themeResId) {
        DisappearingMessages(
            State(expiryMode = ExpiryMode.AfterSend(43200)).toUiState(),
            modifier = Modifier.size(400.dp, 600.dp)
        )
    }
}
