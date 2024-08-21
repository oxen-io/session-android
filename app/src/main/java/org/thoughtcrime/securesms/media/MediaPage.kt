package org.thoughtcrime.securesms.media

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.CrossFade
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import network.loki.messenger.R
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import kotlin.math.ceil

private val MEDIA_SPACING = 2.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaPage(
    nestedScrollConnection: NestedScrollConnection,
    content: TabContent?,
    selectedItemIDs: Set<Long>,
    onItemClicked: (MediaOverviewItem) -> Unit,
    onItemLongClicked: ((Long) -> Unit)?,
) {
    val columnCount = LocalContext.current.resources.getInteger(R.integer.media_overview_cols)

    Crossfade(content, label = "Media content animation") { state ->
        when {
            state == null -> {
                // Loading state
            }

            state.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.media_overview_activity__no_media),
                        style = LocalType.current.base,
                        color = LocalColors.current.text
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .nestedScroll(nestedScrollConnection)
                        .fillMaxSize()
                        .padding(MEDIA_SPACING),
                    verticalArrangement = Arrangement.spacedBy(MEDIA_SPACING)
                ) {
                    for ((header, thumbnails) in state) {
                        stickyHeader {
                            AttachmentHeader(text = header)
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

}

@Composable
@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
private fun ThumbnailRow(
    columnCount: Int,
    thumbnails: List<MediaOverviewItem>,
    rowIndex: Int,
    onItemClicked: (MediaOverviewItem) -> Unit,
    onItemLongClicked: ((Long) -> Unit)?,
    selectedItemIDs: Set<Long>
) {
    Row(horizontalArrangement = Arrangement.spacedBy(MEDIA_SPACING)) {
        repeat(columnCount) { columnIndex ->
            val item = thumbnails.getOrNull(rowIndex * columnCount + columnIndex)
            if (item != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .let {
                            when {
                                onItemLongClicked != null -> {
                                    it.combinedClickable(
                                        onClick = { onItemClicked(item) },
                                        onLongClick = { onItemLongClicked(item.id) }
                                    )
                                }

                                else -> {
                                    it.clickable { onItemClicked(item) }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val uri = item.thumbnailUri

                    if (uri != null) {
                        GlideImage(
                            DecryptableStreamUriLoader.DecryptableUri(uri),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            contentDescription = null,
                            transition = CrossFade,
                        ) {
                            it.diskCacheStrategy(DiskCacheStrategy.NONE)
                        }
                    } else {
                        // The resource given by the placeholder needs tinting according to our theme.
                        // But the missing thumbnail picture does not.
                        var (placeholder, shouldTint) = if (item.hasPlaceholder) {
                            item.placeholder(LocalContext.current) to true
                        } else {
                            R.drawable.ic_missing_thumbnail_picture to false
                        }

                        if (placeholder == 0) {
                            placeholder = R.drawable.ic_missing_thumbnail_picture
                            shouldTint = false
                        }

                        Image(
                            painter = painterResource(placeholder),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Inside,
                            colorFilter = if (shouldTint) {
                                ColorFilter.tint(LocalColors.current.textSecondary)
                            } else {
                                null
                            }
                        )
                    }

                    when {
                        item.showPlayOverlay -> {
                            // The code below is translated from thumbnail_view.xml:
                            // Trying to show a green play button on a white background.
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.White, shape = CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    modifier = Modifier.padding(start = LocalDimensions.current.xxxsSpacing),
                                    painter = painterResource(R.drawable.triangle_right),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(LocalColors.current.primary)
                                )
                            }
                        }
                    }


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