package org.thoughtcrime.securesms.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalType
import kotlin.math.ceil

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MediaOverviewScreen(
    viewModel: MediaOverviewViewModel,
    onClose: () -> Unit,
) {
    val selected by viewModel.selectedItemIDs.collectAsState()
    val selectionMode by viewModel.inSelectionMode.collectAsState()

    val topAppBarState = rememberTopAppBarState()

    val appBarScrollBehavior = if (selectionMode) {
        TopAppBarDefaults.pinnedScrollBehavior(topAppBarState)
    } else {
        TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)
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
            TopAppBar(
                title = {
                    if (selectionMode) {
                        Text(selected.size.toString())
                    } else {
                        Text(viewModel.title.collectAsState().value)
                    }
                },
                navigationIcon = {
                    IconButton(viewModel::onBackClicked) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(R.string.AccessibilityId_done),
                            tint = LocalColors.current.text
                        )
                    }
                },
                scrollBehavior = appBarScrollBehavior,
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = viewModel::onSaveClicked) {
                            Icon(
                                painterResource(R.drawable.ic_baseline_save_24),
                                contentDescription = stringResource(R.string.save),
                                tint = LocalColors.current.text,
                            )
                        }

                        IconButton(onClick = viewModel::onDeleteClicked) {
                            Icon(
                                painterResource(R.drawable.ic_baseline_delete_24),
                                contentDescription = stringResource(R.string.delete),
                                tint = LocalColors.current.text,
                            )
                        }

                        IconButton(onClick = viewModel::onSelectAllClicked) {
                            Icon(
                                painterResource(R.drawable.ic_baseline_select_all_24),
                                contentDescription = stringResource(R.string.MediaOverviewActivity_Select_all),
                                tint = LocalColors.current.text,
                            )
                        }
                    }
                })
        }
    ) { paddings ->
        Column(
            modifier = Modifier
                .padding(paddings)
                .fillMaxSize()
        ) {
            val pagerState = rememberPagerState(pageCount = { MediaOverviewTab.entries.size })
            val selectedTab by viewModel.selectedTab.collectAsState()
            LaunchedEffect(selectedTab) {
                pagerState.animateScrollToPage(selectedTab.ordinal)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaPage(
    nestedScrollConnection: NestedScrollConnection,
    content: TabContent?,
    selectedItemIDs: Set<Long>,
    onItemClicked: (Long) -> Unit,
    onItemLongClicked: ((Long) -> Unit)?,
) {
    val columnCount = 3

    when {
        content == null -> {
            // Loading state
        }

        content.isEmpty() -> {
            // Empty state
        }

        else -> {
            LazyColumn(modifier = Modifier.nestedScroll(nestedScrollConnection)) {
                for ((header, thumbnails) in content) {
                    stickyHeader {
                        Text(text = header)
                    }

                    val numRows = ceil(thumbnails.size / columnCount.toFloat()).toInt()

                    // Row of thumbnails
                    items(numRows) { rowIndex ->
                        ThumbnailRow(
                            columnCount = columnCount,
                            thumbnails = thumbnails,
                            rowIndex = rowIndex,
                            onItemClicked = onItemClicked,
                            onItemLongClicked = onItemLongClicked,
                            selectedItemIDs = selectedItemIDs
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
private fun ThumbnailRow(
    columnCount: Int,
    thumbnails: List<MediaOverviewItem>,
    rowIndex: Int,
    onItemClicked: (Long) -> Unit,
    onItemLongClicked: ((Long) -> Unit)?,
    selectedItemIDs: Set<Long>
) {
    Row {
        repeat(columnCount) { columnIndex ->
            val item = thumbnails.getOrNull(rowIndex * columnCount + columnIndex)
            if (item != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .let {
                            if (onItemLongClicked != null) {
                                it.combinedClickable(
                                    onClick = { onItemClicked(item.id) },
                                    onLongClick = { onItemLongClicked(item.id) }
                                )
                            } else {
                                it.clickable { onItemClicked(item.id) }
                            }
                        }
                ) {
                    GlideImage(
                        item.slide.thumbnailUri!!,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = null,
                    )

                    Crossfade(
                        modifier = Modifier.fillMaxSize(),
                        targetState = item.id in selectedItemIDs,
                        label = "Showing selected state"
                    ) { selected ->
                        if (selected) {
                            Image(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentScale = ContentScale.Inside,
                                painter = painterResource(R.drawable.ic_check_white_48dp),
                                contentDescription = stringResource(R.string.AccessibilityId_select),
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
