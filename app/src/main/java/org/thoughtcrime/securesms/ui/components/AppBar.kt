package org.thoughtcrime.securesms.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.theme.ThemeColors

@Preview
@Composable
fun AppBarPreview(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        AppBar(title = "Title", {}, {})
    }
}

@Composable
fun AppBar(title: String, onClose: () -> Unit = {}, onBack: (() -> Unit)? = null) {
    Row(modifier = Modifier.height(LocalDimensions.current.appBarHeight), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(LocalDimensions.current.appBarHeight)) {
            onBack?.let {
                IconButton(onClick = it) {
                    Icon(painter = painterResource(id = R.drawable.ic_prev), contentDescription = "back")
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(text = title, style = LocalType.current.h4)
        Spacer(modifier = Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(LocalDimensions.current.appBarHeight)) {
            IconButton(onClick = onClose) {
                Icon(painter = painterResource(id = R.drawable.ic_x), contentDescription = "close")
            }
        }
    }
}
