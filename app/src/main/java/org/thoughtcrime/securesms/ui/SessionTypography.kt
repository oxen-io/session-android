package org.thoughtcrime.securesms.ui

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontFamily.Companion.Monospace
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

fun boldStyle(size: TextUnit) = TextStyle.Default.copy(
    fontSize = size,
    lineHeight = size * 1.2,
    fontWeight = FontWeight.Bold,
)

fun defaultStyle(size: TextUnit, fontFamily: FontFamily? = TextStyle.Default.fontFamily) = TextStyle.Default.copy(
    fontSize = size,
    lineHeight = size * 1.2,
    fontFamily = fontFamily
)

val xl = defaultStyle(18.sp)

val large = defaultStyle(16.sp)
val largeBold = boldStyle(16.sp)

val base = defaultStyle(14.sp)
val baseBold = boldStyle(14.sp)
val baseMonospace = defaultStyle(14.sp, fontFamily = Monospace)

val small = defaultStyle(12.sp)
val smallBold = boldStyle(12.sp)
val smallMonospace = defaultStyle(12.sp, fontFamily = Monospace)

val extraSmall = defaultStyle(11.sp)
val extraSmallBold = boldStyle(11.sp)
val extraSmallMonospace = defaultStyle(11.sp, fontFamily = Monospace)

val fine = defaultStyle(9.sp)

val h1 = boldStyle(36.sp)
val h2 = boldStyle(32.sp)
val h3 = boldStyle(29.sp)
val h4 = boldStyle(26.sp)
val h5 = boldStyle(23.sp)
val h6 = boldStyle(20.sp)
val h7 = boldStyle(18.sp)
val h8 = boldStyle(16.sp)
val h9 = boldStyle(14.sp)

val sessionTypography = Typography(
    h1 = h1,
    h2 = h2,
    h3 = h3,
    h4 = h4,
    h5 = h5,
    h6 = h6,
)
