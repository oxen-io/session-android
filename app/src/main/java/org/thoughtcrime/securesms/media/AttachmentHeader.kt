package org.thoughtcrime.securesms.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType

@Composable
fun AttachmentHeader(
    text: String,
    modifier: Modifier = Modifier
){
    Text(
        modifier = modifier
            .background(LocalColors.current.background)
            .fillMaxWidth()
            .padding(
                horizontal = LocalDimensions.current.smallSpacing,
                vertical = LocalDimensions.current.xsSpacing
            ),
        text = text,
        style = LocalType.current.xl,
        color = LocalColors.current.text
    )
}