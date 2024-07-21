package org.thoughtcrime.securesms.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.color.LocalColors

class DialogButtonModel(
    val text: GetString,
    val contentDescription: GetString = text,
    val color: Color = Color.Unspecified,
    val dismissOnClick: Boolean = true,
    val onClick: () -> Unit = {},
)

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    text: String? = null,
    content: @Composable () -> Unit = {},
    buttons: List<DialogButtonModel>? = null
) {
    androidx.compose.material.AlertDialog(
        onDismissRequest,
        shape = MaterialTheme.shapes.small,
        backgroundColor = LocalColors.current.backgroundSecondary,
        buttons = {
            Box {
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_dialog_x),
                        tint = LocalColors.current.text,
                        contentDescription = "back"
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = LocalDimensions.current.smallItemSpacing)
                            .padding(horizontal = LocalDimensions.current.smallItemSpacing)
                    ) {
                        title?.let {
                            Text(
                                it,
                                textAlign = TextAlign.Center,
                                style = h7,
                                modifier = Modifier.padding(bottom = LocalDimensions.current.xxsItemSpacing)
                            )
                        }
                        text?.let {
                            Text(
                                it,
                                textAlign = TextAlign.Center,
                                style = large,
                                modifier = Modifier.padding(bottom = LocalDimensions.current.xxsItemSpacing)
                            )
                        }
                        content()
                    }
                    buttons?.takeIf { it.isNotEmpty() }?.let {
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            it.forEach {
                                DialogButton(
                                    text = it.text(),
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .contentDescription(it.contentDescription())
                                        .weight(1f),
                                    color = it.color
                                ) {
                                    it.onClick()
                                    if (it.dismissOnClick) onDismissRequest()
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun DialogButton(text: String, modifier: Modifier, color: Color = Color.Unspecified, onClick: () -> Unit) {
    TextButton(
        modifier = modifier,
        shape = RectangleShape,
        onClick = onClick
    ) {
        Text(
            text,
            color = color.takeOrElse { LocalColors.current.text },
            style = largeBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(
                top = LocalDimensions.current.smallItemSpacing,
                bottom = LocalDimensions.current.itemSpacing
            )
        )
    }
}
