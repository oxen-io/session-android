package org.thoughtcrime.securesms.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.color.LocalColors
import kotlin.math.absoluteValue
import kotlin.math.sign

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoxScope.HorizontalPagerIndicator(pagerState: PagerState) {
    if (pagerState.pageCount >= 2) Card(
        shape = pillShape,
        backgroundColor = Color.Black.copy(alpha = 0.4f),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(8.dp)
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            ClickableHorizontalPagerIndicator(
                pagerState = pagerState,
                pageCount = pagerState.pageCount,
                activeColor = Color.White,
                inactiveColor = LocalColors.current.textSecondary)
        }
    }
}

internal interface PagerStateBridge {
    val currentPage: Int
    val currentPageOffset: Float
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClickableHorizontalPagerIndicator(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier,
    pageIndexMapping: (Int) -> Int = { it },
    activeColor: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current),
    inactiveColor: Color = activeColor.copy(ContentAlpha.disabled),
    indicatorWidth: Dp = 8.dp,
    indicatorHeight: Dp = indicatorWidth,
    spacing: Dp = indicatorWidth,
    indicatorShape: Shape = CircleShape,
) {
    val scope = rememberCoroutineScope()

    val stateBridge = remember(pagerState) {
        object : PagerStateBridge {
            override val currentPage: Int
                get() = pagerState.currentPage
            override val currentPageOffset: Float
                get() = pagerState.currentPageOffsetFraction
        }
    }

    HorizontalPagerIndicator(
        pagerState = stateBridge,
        pageCount = pageCount,
        modifier = modifier,
        pageIndexMapping = pageIndexMapping,
        activeColor = activeColor,
        inactiveColor = inactiveColor,
        indicatorHeight = indicatorHeight,
        indicatorWidth = indicatorWidth,
        spacing = spacing,
        indicatorShape = indicatorShape,
    ) {
        scope.launch {
            pagerState.animateScrollToPage(it)
        }
    }
}

@Composable
private fun HorizontalPagerIndicator(
    pagerState: PagerStateBridge,
    pageCount: Int,
    modifier: Modifier = Modifier,
    pageIndexMapping: (Int) -> Int = { it },
    activeColor: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current),
    inactiveColor: Color = activeColor.copy(ContentAlpha.disabled),
    indicatorWidth: Dp = 8.dp,
    indicatorHeight: Dp = indicatorWidth,
    spacing: Dp = indicatorWidth,
    indicatorShape: Shape = CircleShape,
    onIndicatorClick: (Int) -> Unit,
) {

    val indicatorWidthPx = LocalDensity.current.run { indicatorWidth.roundToPx() }
    val spacingPx = LocalDensity.current.run { spacing.roundToPx() }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            repeat(pageCount) {
                Box(
                    modifier = Modifier
                        .size(width = indicatorWidth, height = indicatorHeight)
                        .clip(indicatorShape)
                        .background(color = inactiveColor)
                        .clickable { onIndicatorClick(it) }  //modified here
                )
            }
        }

        Box(
            Modifier
                .offset {
                    val position = pageIndexMapping(pagerState.currentPage)
                    val offset = pagerState.currentPageOffset
                    val next = pageIndexMapping(pagerState.currentPage + offset.sign.toInt())
                    val scrollPosition = ((next - position) * offset.absoluteValue + position)
                        .coerceIn(
                            0f,
                            (pageCount - 1)
                                .coerceAtLeast(0)
                                .toFloat()
                        )

                    IntOffset(
                        x = ((spacingPx + indicatorWidthPx) * scrollPosition).toInt(),
                        y = 0
                    )
                }
                .size(width = indicatorWidth, height = indicatorHeight)
                .then(
                    if (pageCount > 0) Modifier.background(
                        color = activeColor,
                        shape = indicatorShape,
                    )
                    else Modifier
                )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.CarouselPrevButton(pagerState: PagerState) {
    CarouselButton(pagerState, pagerState.canScrollBackward, R.drawable.ic_prev, -1)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.CarouselNextButton(pagerState: PagerState) {
    CarouselButton(pagerState, pagerState.canScrollForward, R.drawable.ic_next, 1)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.CarouselButton(
    pagerState: PagerState,
    enabled: Boolean,
    @DrawableRes id: Int,
    delta: Int
) {
    if (pagerState.pageCount <= 1) Spacer(modifier = Modifier.width(32.dp))
    else {
        val animationScope = rememberCoroutineScope()
        IconButton(
            modifier = Modifier
                .width(40.dp)
                .align(Alignment.CenterVertically),
            enabled = enabled,
            onClick = { animationScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + delta) } }) {
            Icon(
                painter = painterResource(id = id),
                contentDescription = null,
            )
        }
    }
}
