package org.thoughtcrime.securesms.media

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.ActivityNotFoundException
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.components.SessionTabRow
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType

@OptIn(
    ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class,
)
@Composable
fun MediaOverviewScreen(
    viewModel: MediaOverviewViewModel,
    onClose: () -> Unit,
) {
    val selectedItems by viewModel.selectedItemIDs.collectAsState()
    val selectionMode by viewModel.inSelectionMode.collectAsState()
    val topAppBarState = rememberTopAppBarState()
    var showingDeleteConfirmation by remember { mutableStateOf(false) }
    var showingSaveAttachmentWarning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val requestStoragePermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.onSaveClicked()
            } else {
                Toast.makeText(
                    context,
                    R.string.permissionsCameraDenied,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

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
                            R.string.attachmentsErrorOpen,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                is MediaOverviewEvent.ShowSaveAttachmentError -> {
                    Toast.makeText(context, R.string.attachmentsSaveError, Toast.LENGTH_LONG).show()
                }

                is MediaOverviewEvent.ShowSaveAttachmentSuccess -> {
                    Toast.makeText(context, R.string.saved, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(appBarScrollBehavior.nestedScrollConnection),
        topBar = {
            MediaOverviewTopAppBar(
                selectionMode = selectionMode,
                title = viewModel.title.collectAsState().value,
                onBackClicked = viewModel::onBackClicked,
                onSaveClicked = { showingSaveAttachmentWarning = true },
                onDeleteClicked = { showingDeleteConfirmation = true },
                onSelectAllClicked = viewModel::onSelectAllClicked,
                numSelected = selectedItems.size,
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

            SessionTabRow(
                pagerState = pagerState,
                titles = MediaOverviewTab.entries.map { it.titleResId }
            )

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
                        val haptics = LocalHapticFeedback.current

                        MediaPage(
                            content = content.value?.mediaContent,
                            selectedItemIDs = selectedItems,
                            onItemClicked = viewModel::onItemClicked,
                            nestedScrollConnection = appBarScrollBehavior.nestedScrollConnection,
                            onItemLongClicked = if(canLongPress){{
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.onItemLongClicked(it)
                            }} else null
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
            numSelected = selectedItems.size
        )
    }

    if (showingSaveAttachmentWarning) {
        SaveAttachmentWarningDialog(
            onDismissRequest = { showingSaveAttachmentWarning = false },
            onAccepted = {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    requestStoragePermission.launch(WRITE_EXTERNAL_STORAGE)
                } else {
                    viewModel.onSaveClicked()
                }
            },
            numSelected = selectedItems.size
        )
    }

    val showingActionDialog = viewModel.showingActionProgress.collectAsState().value
    if (showingActionDialog != null) {
        ActionProgressDialog(showingActionDialog)
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
        title = context.getString(R.string.warning),
        text = context.resources.getString(R.string.attachmentsWarning),
        buttons = listOf(
            DialogButtonModel(GetString(R.string.save), color = LocalColors.current.danger, onClick = onAccepted),
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
            R.plurals.deleteMessage, numSelected
        ),
        buttons = listOf(
            DialogButtonModel(GetString(R.string.delete), color = LocalColors.current.danger, onClick = onAccepted),
            DialogButtonModel(GetString(android.R.string.cancel), dismissOnClick = true)
        )
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ActionProgressDialog(
    text: String
) {
    BasicAlertDialog(
        onDismissRequest = {},
    ) {
        Row(
            modifier = Modifier
                .background(LocalColors.current.background, shape = MaterialTheme.shapes.medium)
                .padding(LocalDimensions.current.mediumSpacing),
            horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(color = LocalColors.current.primary)
            Text(
                text,
                style = LocalType.current.large,
                color = LocalColors.current.text
            )
        }
    }
}

private val MediaOverviewTab.titleResId: Int
    get() = when (this) {
        MediaOverviewTab.Media -> R.string.media
        MediaOverviewTab.Documents -> R.string.files
    }