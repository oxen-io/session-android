package org.thoughtcrime.securesms.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalDimensions = staticCompositionLocalOf { Dimensions() }

data class Dimensions(
    val xxxsSpacing: Dp = 4.dp,
    val xxsSpacing: Dp = 8.dp,
    val xsSpacing: Dp = 12.dp,
    val smallSpacing: Dp = 16.dp,
    val spacing: Dp = 24.dp,
    val mediumSpacing: Dp = 36.dp,
    val xlargeSpacing: Dp = 64.dp,

    val appBarHeight: Dp = 64.dp,
    val minItemButtonHeight: Dp = 50.dp,
    val minLargeItemButtonHeight: Dp = 60.dp,

    val indicatorHeight: Dp = 4.dp,
    val borderStroke: Dp = 1.dp
)
