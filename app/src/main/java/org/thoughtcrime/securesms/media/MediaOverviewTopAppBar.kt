package org.thoughtcrime.securesms.media

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalType

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MediaOverviewTopAppBar(
    selectionMode: Boolean,
    selected: Set<Long>,
    title: String,
    onBackClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onSelectAllClicked: () -> Unit,
    appBarScrollBehavior: TopAppBarScrollBehavior
) {
    TopAppBar(
        title = {
            Text(
                text = if (selectionMode) {
                    selected.size.toString()
                } else {
                    title
                },
                style = LocalType.current.h6,
                color = LocalColors.current.text,
            )
        },
        navigationIcon = {
            IconButton(onBackClicked) {
                Icon(
                    Icons.AutoMirrored.Default.ArrowBack,
                    contentDescription = stringResource(R.string.AccessibilityId_done),
                    tint = LocalColors.current.text
                )
            }
        },
        scrollBehavior = appBarScrollBehavior,
        actions = {
            Crossfade(selectionMode, label = "Action icons animation") { mode ->
                if (mode) {
                    Row {
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
                }
            }
        })
}