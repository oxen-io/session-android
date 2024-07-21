package org.thoughtcrime.securesms.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalDimensions = staticCompositionLocalOf { Dimensions() }

data class Dimensions(
    val xxxsItemSpacing: Dp = 4.dp,
    val xxsItemSpacing: Dp = 8.dp,
    val xsItemSpacing: Dp = 12.dp,
    val smallItemSpacing: Dp = 16.dp,
    val itemSpacing: Dp = 24.dp,

    val xxxsMargin: Dp = 8.dp,
    val xxsMargin: Dp = 12.dp,
    val xsMargin: Dp = 16.dp,
    val smallMargin: Dp = 24.dp,
    val margin: Dp = 32.dp,
    val onboardingMargin: Dp = 36.dp,
    val largeMargin: Dp = 64.dp,
    val homeEmptyViewMargin: Dp = 50.dp,

    val dividerIndent: Dp = 80.dp,
    val appBarHeight: Dp = 64.dp,
    val minScrollableViewHeight: Dp = 200.dp,
    val minLargeItemButtonHeight: Dp = 60.dp,

    val indicatorHeight: Dp = 4.dp,
    val borderStroke: Dp = 1.dp
)
