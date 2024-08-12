package org.thoughtcrime.securesms.media

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalType

@OptIn(
    ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class,
)
@Composable
fun MediaOverviewScreen(
    viewModel: MediaOverviewViewModel,
    onClose: () -> Unit,
) {
    val selected by viewModel.selectedItemIDs.collectAsState()
    val selectionMode by viewModel.inSelectionMode.collectAsState()
    val topAppBarState = rememberTopAppBarState()
    var showingDeleteConfirmation by remember { mutableStateOf(false) }
    var showingSaveAttachmentWarning by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // In selection mode, the app bar should not be scrollable and should be pinned
    val appBarScrollBehavior = if (selectionMode) {
        TopAppBarDefaults.pinnedScrollBehavior(topAppBarState, canScroll = { false })
    } else {
        TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)
    }

    // Reset the top app bar offset (so that it shows up) when entering selection mode
    LaunchedEffect(selectionMode) {
        if (selectionMode) {
            topAppBarState.heightOffset = 0f
        }
    }

    BackHandler(onBack = viewModel::onBackClicked)

    // Event handling
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                MediaOverviewEvent.Close -> onClose()
                is MediaOverviewEvent.NavigateToActivity -> {
                    try {
                        context.startActivity(event.intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(
                            context,
                            R.string.ConversationItem_unable_to_open_media,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                is MediaOverviewEvent.ShowSaveAttachmentError -> {
                    val message = context.resources.getQuantityText(
                        R.plurals.ConversationFragment_error_while_saving_attachments_to_sd_card,
                        event.errorCount
                    )
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }

                is MediaOverviewEvent.ShowSaveAttachmentSuccess -> {
                    val message = if (event.directory.isNotBlank()) {
                        context.resources.getString(
                            R.string.SaveAttachmentTask_saved_to,
                            event.directory
                        )
                    } else {
                        context.resources.getString(R.string.SaveAttachmentTask_saved)
                    }

                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(appBarScrollBehavior.nestedScrollConnection),
        topBar = {
            MediaOverviewTopAppBar(
                selectionMode = selectionMode,
                selected = selected,
                title = viewModel.title.collectAsState().value,
                onBackClicked = viewModel::onBackClicked,
                onSaveClicked = { showingSaveAttachmentWarning = true },
                onDeleteClicked = { showingDeleteConfirmation = true },
                onSelectAllClicked = viewModel::onSelectAllClicked,
                appBarScrollBehavior = appBarScrollBehavior
            )
        }
    ) { paddings ->
        Column(
            modifier = Modifier
                .padding(paddings)
                .fillMaxSize()
        ) {
            val pagerState = rememberPagerState(pageCount = { MediaOverviewTab.entries.size })
            val selectedTab by viewModel.selectedTab.collectAsState()

            // Apply "selectedTab" view model state to pager
            LaunchedEffect(selectedTab) {
                pagerState.animateScrollToPage(selectedTab.ordinal)
            }

            // Apply "selectedTab" pager state to view model
            LaunchedEffect(pagerState.currentPage) {
                viewModel.onTabItemClicked(MediaOverviewTab.entries[pagerState.currentPage])
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                MediaOverviewTab.entries.forEach { tab ->
                    TextButton(
                        onClick = { viewModel.onTabItemClicked(tab) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(tab.titleResId),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = LocalType.current.large,
                            color = if (selectedTab == tab) {
                                LocalColors.current.text
                            } else {
                                LocalColors.current.textSecondary
                            }
                        )
                    }
                }
            }

            val content = viewModel.mediaListState.collectAsState()
            val canLongPress = viewModel.canLongPress.collectAsState().value

            HorizontalPager(
                pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { index ->
                when (MediaOverviewTab.entries[index]) {
                    MediaOverviewTab.Media -> {
                        MediaPage(
                            content = content.value?.mediaContent,
                            selectedItemIDs = selected,
                            onItemClicked = viewModel::onItemClicked,
                            nestedScrollConnection = appBarScrollBehavior.nestedScrollConnection,
                            onItemLongClicked = viewModel::onItemLongClicked.takeIf { canLongPress }
                        )
                    }

                    MediaOverviewTab.Documents -> DocumentsPage(
                        nestedScrollConnection = appBarScrollBehavior.nestedScrollConnection,
                        content = content.value?.documentContent,
                        onItemClicked = viewModel::onItemClicked
                    )
                }
            }
        }
    }

    if (showingDeleteConfirmation) {
        DeleteConfirmationDialog(
            onDismissRequest = { showingDeleteConfirmation = false },
            onAccepted = viewModel::onDeleteClicked,
            numSelected = selected.size
        )
    }

    if (showingSaveAttachmentWarning) {
        SaveAttachmentWarningDialog(
            onDismissRequest = { showingSaveAttachmentWarning = false },
            onAccepted = viewModel::onSaveClicked,
            numSelected = selected.size
        )
    }

    if (viewModel.showSavingProgress.collectAsState().value) {
        SaveAttachmentProgressDialog(selected.size)
    }
}

@Composable
private fun SaveAttachmentWarningDialog(
    onDismissRequest: () -> Unit,
    onAccepted: () -> Unit,
    numSelected: Int,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = context.getString(R.string.ConversationFragment_save_to_sd_card),
        text = context.resources.getQuantityString(
            R.plurals.ConversationFragment_saving_n_media_to_storage_warning,
            numSelected,
            numSelected
        ),
        buttons = listOf(
            DialogButtonModel(GetString(R.string.save), onClick = onAccepted),
            DialogButtonModel(GetString(android.R.string.cancel), dismissOnClick = true)
        )
    )
}

@Composable
private fun DeleteConfirmationDialog(
    onDismissRequest: () -> Unit,
    onAccepted: () -> Unit,
    numSelected: Int,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = context.resources.getQuantityString(
            R.plurals.ConversationFragment_delete_selected_messages, numSelected
        ),
        text = context.resources.getQuantityString(
            R.plurals.ConversationFragment_this_will_permanently_delete_all_n_selected_messages,
            numSelected,
            numSelected,
        ),
        buttons = listOf(
            DialogButtonModel(GetString(R.string.delete), onClick = onAccepted),
            DialogButtonModel(GetString(android.R.string.cancel), dismissOnClick = true)
        )
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SaveAttachmentProgressDialog(
    numSelected: Int,
) {
    val context = LocalContext.current
    BasicAlertDialog(
        onDismissRequest = {},
    ) {
        Row(
            modifier = Modifier
                .background(LocalColors.current.background, shape = RoundedCornerShape(16.dp))
                .padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(color = LocalColors.current.primary)
            Text(
                context.resources.getQuantityString(
                    R.plurals.ConversationFragment_saving_n_attachments,
                    numSelected,
                    numSelected,
                ),
                style = LocalType.current.large,
                color = LocalColors.current.text
            )
        }
    }
}

private val MediaOverviewTab.titleResId: Int
    get() = when (this) {
        MediaOverviewTab.Media -> R.string.MediaOverviewActivity_Media
        MediaOverviewTab.Documents -> R.string.MediaOverviewActivity_Documents
    }

