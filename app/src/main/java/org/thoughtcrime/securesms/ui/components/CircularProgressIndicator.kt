package org.thoughtcrime.securesms.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current
) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = modifier.size(40.dp),
        color = color,
        strokeWidth = 2.dp
    )
}

@Composable
fun SmallCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current
) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = modifier.size(20.dp),
        color = color,
        strokeWidth = 2.dp
    )
}
