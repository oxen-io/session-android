package org.thoughtcrime.securesms.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.thoughtcrime.securesms.conversation.disappearingmessages.ExpiryType
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.RadioOption
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.transparentButtonColors
import kotlin.time.Duration.Companion.days

@Composable
fun RadioButton(
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable RowScope.() -> Unit = {}
) {
    TextButton(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = true,
                role = Role.RadioButton,
                onClick = onClick
            ),
        enabled = enabled,
        colors = transparentButtonColors(),
        onClick = onClick,
        shape = RectangleShape,
        contentPadding = contentPadding
    ) {
        content()

        Spacer(modifier = Modifier.width(20.dp))
        RadioButtonIndicator(
            selected = selected && enabled, // disabled radio shouldn't be selected
            modifier = Modifier
                .size(22.dp)
                .align(Alignment.CenterVertically)
        )
    }
}

@Composable
private fun RadioButtonIndicator(
    selected: Boolean,
    modifier: Modifier
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            selected,
            modifier = Modifier
                .padding(2.5.dp)
                .clip(CircleShape),
            enter = scaleIn(),
            exit = scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = LocalColors.current.primary,
                        shape = CircleShape
                    )
            )
        }
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .border(
                    width = LocalDimensions.current.borderStroke,
                    color = LocalContentColor.current,
                    shape = CircleShape
                )
        ) {}
    }
}

@Composable
fun <T> TitledRadioButton(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = LocalDimensions.current.spacing,
        vertical = LocalDimensions.current.smallSpacing
    ),
    option: RadioOption<T>,
    onClick: () -> Unit
) {
    RadioButton(
        modifier = modifier
            .contentDescription(option.contentDescription),
        onClick = onClick,
        selected = option.selected,
        enabled = option.enabled,
        contentPadding = contentPadding,
        content = {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                Column {
                    Text(
                        text = option.title(),
                        style = LocalType.current.large
                    )
                    option.subtitle?.let {
                        Text(
                            text = it(),
                            style = LocalType.current.extraSmall
                        )
                    }
                }
            }
        }
    )
}

@Preview
@Composable
fun PreviewTextRadioButton() {
    PreviewTheme {
        TitledRadioButton(
            option = RadioOption<ExpiryMode>(
                value = ExpiryType.AFTER_SEND.mode(7.days),
                title = GetString(7.days),
                subtitle = GetString("This is a subtitle"),
                enabled = true,
                selected = true
            )
        ) {}
    }
}

@Preview
@Composable
fun PreviewDisabledTextRadioButton() {
    PreviewTheme {
        TitledRadioButton(
            option = RadioOption<ExpiryMode>(
                value = ExpiryType.AFTER_SEND.mode(7.days),
                title = GetString(7.days),
                subtitle = GetString("This is a subtitle"),
                enabled = false,
                selected = true
            )
        ) {}
    }
}

@Preview
@Composable
fun PreviewDeselectedTextRadioButton() {
    PreviewTheme {
        TitledRadioButton(
            option = RadioOption<ExpiryMode>(
                value = ExpiryType.AFTER_SEND.mode(7.days),
                title = GetString(7.days),
                subtitle = GetString("This is a subtitle"),
                enabled = true,
                selected = false
            )
        ) {}
    }
}