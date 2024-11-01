package org.thoughtcrime.securesms.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.borders
import org.thoughtcrime.securesms.ui.theme.text
import org.thoughtcrime.securesms.ui.theme.textSecondary
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.bold
import kotlin.math.sin

@Preview
@Composable
fun PreviewSessionOutlinedTextField() {
    PreviewTheme {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SessionOutlinedTextField(
                text = "text",
                placeholder = "",
            )

            SessionOutlinedTextField(
                text = "",
                placeholder = "placeholder"
            )

            SessionOutlinedTextField(
                text = "text",
                placeholder = "",
                error = "error"
            )

            SessionOutlinedTextField(
                text = "text onChange after error",
                placeholder = "",
                error = "error",
                isTextErrorColor = false
            )
        }
    }
}

@Composable
fun SessionOutlinedTextField(
    text: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit = {},
    textStyle: TextStyle = LocalType.current.base,
    innerPadding: PaddingValues = PaddingValues(LocalDimensions.current.spacing),
    placeholder: String = "",
    onContinue: () -> Unit = {},
    error: String? = null,
    isTextErrorColor: Boolean = error != null,
    enabled: Boolean = true,
    singleLine: Boolean = false,
) {
    BasicTextField(
        value = text,
        onValueChange = onChange,
        modifier = modifier,
        textStyle = textStyle.copy(color = LocalColors.current.text(isTextErrorColor)),
        cursorBrush = SolidColor(LocalColors.current.text(isTextErrorColor)),
        enabled = enabled,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { onContinue() },
            onGo = { onContinue() },
            onSearch = { onContinue() },
            onSend = { onContinue() },
        ),
        singleLine = singleLine,
        decorationBox = { innerTextField ->
            Column(modifier = Modifier.animateContentSize()) {
                Box(
                    modifier = Modifier
                        .border(
                            width = LocalDimensions.current.borderStroke,
                            color = LocalColors.current.borders(error != null),
                            shape = MaterialTheme.shapes.small
                        )
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(innerPadding)
                ) {
                    innerTextField()

                    if (placeholder.isNotEmpty() && text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle.copy(color = LocalColors.current.textSecondary),
                        )
                    }
                }

                error?.let {
                    Spacer(modifier = Modifier.height(LocalDimensions.current.xsSpacing))
                    Text(
                        it,
                        modifier = Modifier
                            .fillMaxWidth()
                            .contentDescription(R.string.AccessibilityId_theError),
                        textAlign = TextAlign.Center,
                        style = LocalType.current.base.bold(),
                        color = LocalColors.current.danger
                    )
                }
            }
        }
    )
}

@Composable
fun AnnotatedTextWithIcon(
    text: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalType.current.base,
    color: Color = Color.Unspecified,
    iconSize: TextUnit = 12.sp
) {
    val myId = "inlineContent"
    val annotated = buildAnnotatedString {
        append(text)
        appendInlineContent(myId, "[icon]")
    }

    val inlineContent = mapOf(
        Pair(
            myId,
            InlineTextContent(
                Placeholder(
                    width = iconSize,
                    height = iconSize,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                )
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.padding(1.dp),
                    tint = color
                )
            }
        )
    )

    Text(
        text = annotated,
        modifier = modifier.fillMaxWidth(),
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        inlineContent = inlineContent
    )
}