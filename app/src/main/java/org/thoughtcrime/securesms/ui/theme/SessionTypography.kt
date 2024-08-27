package org.thoughtcrime.securesms.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp


fun TextStyle.bold() = copy(
    fontWeight = FontWeight.Bold
)

fun TextStyle.monospace() = copy(
    fontFamily = FontFamily.Monospace
)

val sessionTypography = SessionTypography()

data class SessionTypography(
    // Body
    val xl: TextStyle = TextStyle(
        fontSize = 18.sp,
        lineHeight = 21.6.sp,
        fontWeight = FontWeight.Normal
    ),

    val large: TextStyle = TextStyle(
        fontSize = 16.sp,
        lineHeight = 19.2.sp,
        fontWeight = FontWeight.Normal
    ),

    val base: TextStyle = TextStyle(
        fontSize = 14.sp,
        lineHeight = 16.8.sp,
        fontWeight = FontWeight.Normal
    ),

    val small: TextStyle = TextStyle(
        fontSize = 12.sp,
        lineHeight = 14.4.sp,
        fontWeight = FontWeight.Normal
    ),

    val extraSmall: TextStyle = TextStyle(
        fontSize = 11.sp,
        lineHeight = 13.2.sp,
        fontWeight = FontWeight.Normal
    ),

    val fine: TextStyle = TextStyle(
        fontSize = 9.sp,
        lineHeight = 10.8.sp,
        fontWeight = FontWeight.Normal
    ),

    // Headings
    val h1: TextStyle = TextStyle(
        fontSize = 36.sp,
        lineHeight = 43.2.sp,
        fontWeight = FontWeight.Bold
    ),

    val h2: TextStyle = TextStyle(
        fontSize = 32.sp,
        lineHeight = 38.4.sp,
        fontWeight = FontWeight.Bold
    ),

    val h3: TextStyle = TextStyle(
        fontSize = 29.sp,
        lineHeight = 34.8.sp,
        fontWeight = FontWeight.Bold
    ),

    val h4: TextStyle = TextStyle(
        fontSize = 26.sp,
        lineHeight = 31.2.sp,
        fontWeight = FontWeight.Bold
    ),

    val h5: TextStyle = TextStyle(
        fontSize = 23.sp,
        lineHeight = 27.6.sp,
        fontWeight = FontWeight.Bold
    ),

    val h6: TextStyle = TextStyle(
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold
    ),

    val h7: TextStyle = TextStyle(
        fontSize = 18.sp,
        lineHeight = 21.6.sp,
        fontWeight = FontWeight.Bold
    ),

    val h8: TextStyle = TextStyle(
        fontSize = 16.sp,
        lineHeight = 19.2.sp,
        fontWeight = FontWeight.Bold
    ),

    val h9: TextStyle = TextStyle(
        fontSize = 14.sp,
        lineHeight = 16.8.sp,
        fontWeight = FontWeight.Bold
    )
) {

    // An opinionated override of Material's defaults
    @Composable
    fun asMaterialTypography() = Typography(
        // Display
        displayLarge = h1,
        displayMedium = h1,
        displaySmall = h1,

        // Headline
        headlineLarge = h2,
        headlineMedium = h3,
        headlineSmall = h4,

        // Title
        titleLarge = h5,
        titleMedium = h6,
        titleSmall = h7,

        // Body
        bodyLarge = large,
        bodyMedium = base,
        bodySmall = small,

        // Label
        labelLarge = extraSmall,
        labelMedium = fine,
        labelSmall = fine
    )
}


