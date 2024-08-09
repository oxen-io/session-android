package org.thoughtcrime.securesms.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalType

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MediaOverviewScreen(
    viewModel: MediaOverviewViewModel,
    onClose: () -> Unit,
) {
    val selected by viewModel.selectedItemIDs.collectAsState()
    val selectionMode by viewModel.inSelectionMode.collectAsState()
    val topAppBarState = rememberTopAppBarState()
    var showingDeleteConfirmation by remember { mutableStateOf(false) }

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

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                MediaOverviewEvent.Close -> onClose()
                is MediaOverviewEvent.NavigateToMediaDetail -> {
                    TODO()
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
                onSaveClicked = viewModel::onSaveClicked,
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

                    MediaOverviewTab.Documents -> DocumentsPage()
                }
            }
        }
    }

    if (showingDeleteConfirmation) {
        val context = LocalContext.current

        AlertDialog(
            onDismissRequest = { showingDeleteConfirmation = false },
            title = context.resources.getQuantityString(
                R.plurals.ConversationFragment_delete_selected_messages, selected.size
            ),
            text = context.resources.getQuantityString(
                R.plurals.ConversationFragment_this_will_permanently_delete_all_n_selected_messages,
                selected.size,
                selected.size
            ),
            buttons = listOf(
                DialogButtonModel(GetString(R.string.delete), onClick = viewModel::onDeleteClicked),
                DialogButtonModel(GetString(android.R.string.cancel), dismissOnClick = true)
            )
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MediaOverviewTopAppBar(
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
            if (selectionMode) {
                Text(selected.size.toString())
            } else {
                Text(title)
            }
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

private val MediaOverviewTab.titleResId: Int
    get() = when (this) {
        MediaOverviewTab.Media -> R.string.MediaOverviewActivity_Media
        MediaOverviewTab.Documents -> R.string.MediaOverviewActivity_Documents
    }

@Composable
private fun DocumentsPage() {
    Text("Documents Page")
}

