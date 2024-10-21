package org.thoughtcrime.securesms.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.times
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.URL_KEY
import org.thoughtcrime.securesms.copyURLToClipboard
import org.thoughtcrime.securesms.openUrl
import org.thoughtcrime.securesms.ui.components.CircularProgressIndicator
import org.thoughtcrime.securesms.ui.components.annotatedStringResource
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.bold


class DialogButtonModel(
    val text: GetString,
    val color: Color = Color.Unspecified,
    val dismissOnClick: Boolean = true,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {},
)

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    text: String? = null,
    maxLines:  Int? = null,
    buttons: List<DialogButtonModel>? = null,
    showCloseButton: Boolean = false,
    content: @Composable () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = if(title != null) AnnotatedString(title) else null,
        text = if(text != null) AnnotatedString(text) else null,
        maxLines = maxLines,
        buttons = buttons,
        showCloseButton = showCloseButton,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: AnnotatedString? = null,
    text: AnnotatedString? = null,
    maxLines: Int? = null,
    buttons: List<DialogButtonModel>? = null,
    showCloseButton: Boolean = false,
    content: @Composable () -> Unit = {}
) {
    BasicAlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        content = {
            DialogBg {
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
                                text = it,
                                textAlign = TextAlign.Center,
                                style = LocalType.current.h7,
                                modifier = Modifier.padding(bottom = LocalDimensions.current.xxsSpacing)
                                    .qaTag(stringResource(R.string.AccessibilityId_modalTitle))
                            )
                        }
                        text?.let {
                            val textStyle = LocalType.current.large
                            var textModifier = Modifier.padding(bottom = LocalDimensions.current.xxsSpacing)

                            // if we have a maxLines, make the text scrollable
                            if(maxLines != null) {
                                val textHeight = with(LocalDensity.current) {
                                    textStyle.lineHeight.toDp()
                                } * maxLines

                                textModifier = textModifier
                                    .height(textHeight)
                                    .verticalScroll(rememberScrollState())
                            }

                            Text(
                                text = it,
                                textAlign = TextAlign.Center,
                                style = textStyle,
                                modifier = textModifier
                                    .qaTag(stringResource(R.string.AccessibilityId_modalMessage))
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
                                        .qaTag(it.text.string())
                                        .weight(1f),
                                    color = it.color,
                                    enabled = it.enabled
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
fun OpenURLAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    url: String,
    content: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val unformattedText = Phrase.from(context.getText(R.string.urlOpenDescription))
        .put(URL_KEY, url).format()


    AlertDialog(
        modifier = modifier,
        title = AnnotatedString(stringResource(R.string.urlOpen)),
        text = annotatedStringResource(text = unformattedText),
        maxLines = 5,
        showCloseButton = true, // display the 'x' button
        buttons = listOf(
            DialogButtonModel(
                text = GetString(R.string.open),
                color = LocalColors.current.danger,
                onClick = { context.openUrl(url) }
            ),
            DialogButtonModel(
                text = GetString(android.R.string.copyUrl),
                onClick = {
                    context.copyURLToClipboard(url)
                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                }
            )
        ),
        onDismissRequest = onDismissRequest,
        content = content
    )
}

@Composable
fun DialogButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        modifier = modifier,
        shape = RectangleShape,
        enabled = enabled,
        onClick = onClick
    ) {
        val textColor = if(enabled) {
            color.takeOrElse { LocalColors.current.text }
        } else {
            LocalColors.current.disabled
        }

        Text(
            text,
            color = textColor,
            style = LocalType.current.large.bold(),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(
                vertical = LocalDimensions.current.smallSpacing
            )
        )
    }
}

@Composable
fun DialogBg(
    content: @Composable BoxScope.() -> Unit
){
    Box(
        modifier = Modifier
            .background(
                color = LocalColors.current.backgroundSecondary,
                shape = MaterialTheme.shapes.small
            )
            .border(
                width = 1.dp,
                color = LocalColors.current.borders,
                shape = MaterialTheme.shapes.small
            )

    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadingDialog(
    modifier: Modifier = Modifier,
    title: String? = null,
){
    BasicAlertDialog(
        modifier = modifier,
        onDismissRequest = {},
        content = {
            DialogBg {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(LocalDimensions.current.spacing)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(LocalDimensions.current.spacing))

                    title?.let {
                        Text(
                            it,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                .qaTag(stringResource(R.string.AccessibilityId_modalTitle)),
                            style = LocalType.current.large
                        )
                    }
                }
            }
        }
    )
}

@Preview
@Composable
fun PreviewSimpleDialog() {
    PreviewTheme {
        AlertDialog(
            onDismissRequest = {},
            title = stringResource(R.string.warning),
            text = stringResource(R.string.onboardingBackAccountCreation),
            buttons = listOf(
                DialogButtonModel(
                    GetString(stringResource(R.string.cancel)),
                    color = LocalColors.current.danger,
                    onClick = { }
                ),
                DialogButtonModel(
                    GetString(stringResource(R.string.ok))
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
                    text = GetString(R.string.onboardingTos),
                    onClick = {}
                ),
                DialogButtonModel(
                    text = GetString(R.string.onboardingPrivacy),
                    onClick = {}
                )
            ),
            onDismissRequest = {}
        )
    }
}

@Preview
@Composable
fun PreviewOpenURLDialog() {
    PreviewTheme {
        OpenURLAlertDialog(
            url = "https://getsession.org/",
            onDismissRequest = {}
        )
    }
}

@Preview
@Composable
fun PreviewLoadingDialog() {
    PreviewTheme {
        LoadingDialog(
            title = stringResource(R.string.warning)
        )
    }
}
