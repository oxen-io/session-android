package org.thoughtcrime.securesms.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import network.loki.messenger.R

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoxScope.HorizontalPagerIndicator(pagerState: PagerState) {
    if (pagerState.pageCount >= 2) Card(
        shape = RoundedCornerShape(50.dp),
        backgroundColor = Color.Black.copy(alpha = 0.4f),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(8.dp)
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            com.google.accompanist.pager.HorizontalPagerIndicator(
                pagerState = pagerState,
                pageCount = pagerState.pageCount,
                activeColor = Color.White,
                inactiveColor = classicDarkColors[5])
        }
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
