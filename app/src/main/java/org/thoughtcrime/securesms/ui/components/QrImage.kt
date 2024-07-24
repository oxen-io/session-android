package org.thoughtcrime.securesms.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.util.QRCodeUtilities

@Composable
fun QrImage(
    string: String?,
    modifier: Modifier = Modifier,
    contentPadding: Dp = LocalDimensions.current.smallSpacing,
    icon: Int = R.drawable.session_shield
) {
    var bitmap: Bitmap? by remember {
        mutableStateOf(null)
    }

    val scope = rememberCoroutineScope()
    LaunchedEffect(string) {
        if (string != null) scope.launch(Dispatchers.IO) {
            bitmap = (300..500 step 100).firstNotNullOf {
                runCatching { QRCodeUtilities.encode(string, it) }.getOrNull()
            }
        }
    }

    Box(
        modifier = modifier.background(color = LocalColors.current.qrCodeBackground,
            shape = MaterialTheme.shapes.small)
    ) { Content(bitmap, icon, Modifier.padding(contentPadding), backgroundColor = LocalColors.current.qrCodeBackground) }
}

@Composable
private fun Content(
    bitmap: Bitmap?,
    icon: Int,
    modifier: Modifier = Modifier,
    qrColor: Color = LocalColors.current.qrCodeContent,
    backgroundColor: Color,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        AnimatedVisibility(
            visible = bitmap != null,
            enter = fadeIn(),
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    colorFilter = ColorFilter.tint(qrColor),
                    // Use FilterQuality.None to keep QR edges sharp
                    filterQuality = FilterQuality.None
                )
            }
        }

        Icon(
            painter = painterResource(id = icon),
            contentDescription = "",
            tint = qrColor,
            modifier = Modifier
                .size(62.dp)
                .align(Alignment.Center)
                .background(color = backgroundColor)
                .size(66.dp)
        )
    }
}