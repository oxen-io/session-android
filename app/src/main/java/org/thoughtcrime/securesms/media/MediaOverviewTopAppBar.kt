package org.thoughtcrime.securesms.media

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.components.ActionAppBar
import org.thoughtcrime.securesms.ui.components.AppBarBackIcon
import org.thoughtcrime.securesms.ui.theme.LocalColors

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MediaOverviewTopAppBar(
    selectionMode: Boolean,
    title: String,
    onBackClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onSelectAllClicked: () -> Unit,
    appBarScrollBehavior: TopAppBarScrollBehavior
) {
    ActionAppBar(
        title = title,
        navigationIcon = {AppBarBackIcon(onBack = onBackClicked)},
        scrollBehavior = appBarScrollBehavior,
        actionMode = selectionMode,
        actionModeActions = {
            IconButton(onClick = onSaveClicked) {
                Icon(
                    painterResource(R.drawable.ic_baseline_save_24),
                    contentDescription = stringResource(R.string.save),
                    tint = LocalColors.current.text,
                )
            }

            IconButton(onClick = onDeleteClicked) {
                Icon(
                    painterResource(R.drawable.ic_baseline_delete_24),
                    contentDescription = stringResource(R.string.delete),
                    tint = LocalColors.current.text,
                )
            }

            IconButton(onClick = onSelectAllClicked) {
                Icon(
                    painterResource(R.drawable.ic_baseline_select_all_24),
                    contentDescription = stringResource(R.string.MediaOverviewActivity_Select_all),
                    tint = LocalColors.current.text,
                )
            }
        }
    )
}
