package org.thoughtcrime.securesms.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.components.BorderlessButton

class DialogButtonModel(
    val text: GetString,
    val contentDescription: GetString = text,
    val onClick: () -> Unit
)

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    text: String? = null,
    buttons: List<DialogButtonModel>? = null
) {
    androidx.compose.material.AlertDialog(
        onDismissRequest,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = LocalColors.current.backgroundSecondary,
        buttons = {
            Box {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                            .padding(horizontal = 24.dp)
                    ) {
                        title?.let {
                            Text(it, textAlign = TextAlign.Center, style = h7, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        text?.let {
                            Text(it, textAlign = TextAlign.Center, style = large, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                    buttons?.takeIf { it.isNotEmpty() }?.let {
                        Row {
                            it.forEach {
                                TextButton(
                                    modifier = Modifier
                                        .contentDescription(it.contentDescription())
                                        .weight(1f),
                                    shape = RectangleShape,
                                    onClick = {
                                        it.onClick
                                        onDismissRequest()
                                    }
                                ) {
                                    Text(
                                        it.text(),
                                        color = LocalColors.current.text,
                                        style = largeBold,
                                        modifier = Modifier.padding(
                                            top = 16.dp,
                                            bottom = 24.dp
                                        )
                                    )
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
fun DialogButton(text: String, contentDescription: String, onClick: () -> Unit) {
    BorderlessButton(text, modifier = Modifier.contentDescription(contentDescription), onClick = onClick)
}