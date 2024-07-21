package org.thoughtcrime.securesms.ui.components

import androidx.compose.foundation.border
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.color.LocalColors

@Composable
fun Modifier.border() = this.border(
    width = LocalDimensions.current.borderStroke,
    brush = SolidColor(LocalColors.current.borders),
    shape = MaterialTheme.shapes.small
)