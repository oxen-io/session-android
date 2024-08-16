package org.thoughtcrime.securesms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.bold


class DialogButtonModel(
    val text: GetString,
    val contentDescription: GetString = text,
    val color: Color = Color.Unspecified,
    val dismissOnClick: Boolean = true,
    val onClick: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    text: String? = null,
    buttons: List<DialogButtonModel>? = null,
    showCloseButton: Boolean = false,
    content: @Composable () -> Unit = {}
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        content = {
            Box(
                modifier = Modifier.background(
                    color = LocalColors.current.backgroundSecondary,
                    shape = MaterialTheme.shapes.small)
                    .border(
                        width = 1.dp,
                        color = LocalColors.current.borders,
                        shape = MaterialTheme.shapes.small)

            ) {
                // only show the 'x' button is required
                if (showCloseButton) {
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
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = LocalDimensions.current.spacing)
                            .padding(horizontal = LocalDimensions.current.smallSpacing)
                    ) {
                        title?.let {
                            Text(
                                it,
                                textAlign = TextAlign.Center,
                                style = LocalType.current.h7,
                                modifier = Modifier.padding(bottom = LocalDimensions.current.xxsSpacing)
                            )
                        }
                        text?.let {
                            Text(
                                it,
                                textAlign = TextAlign.Center,
                                style = LocalType.current.large,
                                modifier = Modifier.padding(bottom = LocalDimensions.current.xxsSpacing)
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
fun DialogButton(
    text: String,
    modifier: Modifier,
    color: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    TextButton(
        modifier = modifier,
        shape = RectangleShape,
        onClick = onClick
    ) {
        Text(
            text,
            color = color.takeOrElse { LocalColors.current.text },
            style = LocalType.current.large.bold(),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(
                vertical = LocalDimensions.current.smallSpacing
            )
        )
    }
}

@Preview
@Composable
fun PreviewSimpleDialog() {
    PreviewTheme {
        AlertDialog(
            onDismissRequest = {},
            title = stringResource(R.string.warning),
            text = stringResource(R.string.you_cannot_go_back_further_in_order_to_stop_loading_your_account_session_needs_to_quit),
            buttons = listOf(
                DialogButtonModel(
                    GetString(stringResource(R.string.quit)),
                    color = LocalColors.current.danger,
                    onClick = {}
                ),
                DialogButtonModel(
                    GetString(stringResource(R.string.cancel))
                )
            )
        )
    }
}

@Preview
@Composable
fun PreviewXCloseDialog() {
    PreviewTheme {
        AlertDialog(
            title = stringResource(R.string.urlOpen),
            text = stringResource(R.string.urlOpenBrowser),
            showCloseButton = true, // display the 'x' button
            buttons = listOf(
                DialogButtonModel(
                    text = GetString(R.string.activity_landing_terms_of_service),
                    contentDescription = GetString(R.string.AccessibilityId_terms_of_service_button),
                    onClick = {}
                ),
                DialogButtonModel(
                    text = GetString(R.string.activity_landing_privacy_policy),
                    contentDescription = GetString(R.string.AccessibilityId_privacy_policy_button),
                    onClick = {}
                )
            ),
            onDismissRequest = {}
        )
    }
}
