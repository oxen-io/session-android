package org.thoughtcrime.securesms.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.color.divider
import org.thoughtcrime.securesms.ui.h8

private val TITLES = listOf(R.string.sessionRecoveryPassword, R.string.qrScan)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionTabRow(pagerState: PagerState, titles: List<Int>) {
    TabRow(
            backgroundColor = Color.Unspecified,
            selectedTabIndex = pagerState.currentPage,
            contentColor = LocalColors.current.text,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = LocalColors.current.primary,
                    height = LocalDimensions.current.indicatorHeight
                )
            },
            divider = { TabRowDefaults.Divider(color = LocalColors.current.divider) },
            modifier = Modifier
                .height(48.dp)
                .background(color = Color.Unspecified)
    ) {
        val animationScope = rememberCoroutineScope()
        titles.forEachIndexed { i, it ->
            Tab(
                i == pagerState.currentPage,
                onClick = { animationScope.launch { pagerState.animateScrollToPage(i) } },
                selectedContentColor = LocalColors.current.text,
                unselectedContentColor = LocalColors.current.text,
            ) {
                Text(
                    stringResource(id = it),
                    style = h8
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewSessionTabRow(
        @PreviewParameter(SessionColorsParameterProvider::class) colors: Colors
) {
    PreviewTheme(colors) {
        val pagerState = rememberPagerState { TITLES.size }
        SessionTabRow(pagerState = pagerState, titles = TITLES)
    }
}
