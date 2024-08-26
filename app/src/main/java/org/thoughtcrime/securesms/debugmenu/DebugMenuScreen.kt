package org.thoughtcrime.securesms.debugmenu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DebugMenuScreen(
    modifier: Modifier = Modifier,
    debugMenuViewModel: DebugMenuViewModel = viewModel(),
    onClose: () -> Unit
) {
    val uiState by debugMenuViewModel.uiState.collectAsState()

    DebugMenu(
        modifier = modifier,
        uiState = uiState,
        sendCommand = debugMenuViewModel::onCommand,
        onClose = onClose
    )
}
