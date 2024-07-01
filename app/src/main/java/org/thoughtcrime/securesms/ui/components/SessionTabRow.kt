package org.thoughtcrime.securesms.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
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
import org.thoughtcrime.securesms.ui.LocalExtraColors
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.ThemeResPreviewParameterProvider

private val TITLES = listOf(R.string.sessionRecoveryPassword, R.string.qrScan)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionTabRow(pagerState: PagerState, titles: List<Int>) {
    TabRow(
            backgroundColor = Color.Unspecified,
            selectedTabIndex = pagerState.currentPage,
            contentColor = LocalExtraColors.current.prominentButtonColor,
            divider = { TabRowDefaults.Divider(color = MaterialTheme.colors.onPrimary.copy(alpha = TabRowDefaults.DividerOpacity)) },
            modifier = Modifier
                .height(48.dp)
                .background(color = Color.Unspecified)
    ) {
        val animationScope = rememberCoroutineScope()
        titles.forEachIndexed { i, it ->
            Tab(
                i == pagerState.currentPage,
                onClick = { animationScope.launch { pagerState.animateScrollToPage(i) } },
                selectedContentColor = MaterialTheme.colors.onPrimary,
                unselectedContentColor = MaterialTheme.colors.onPrimary,
            ) {
                Text(stringResource(id = it))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewSessionTabRow(
        @PreviewParameter(ThemeResPreviewParameterProvider::class) themeResId: Int
) {
    PreviewTheme(themeResId) {
        val pagerState = rememberPagerState { TITLES.size }
        SessionTabRow(pagerState = pagerState, titles = TITLES)
    }
}
