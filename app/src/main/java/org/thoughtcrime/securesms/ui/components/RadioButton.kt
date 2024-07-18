package org.thoughtcrime.securesms.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.TextButton
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.color.transparentButtonColors

@Composable
fun RadioButton(
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    checked: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable RowScope.() -> Unit = {}
) {
    TextButton(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = checked,
                enabled = true,
                role = Role.RadioButton,
                onClick = onClick
            ),
        colors = transparentButtonColors(),
        onClick = onClick,
        shape = RectangleShape,
        contentPadding = contentPadding
    ) {
        content()
        Spacer(modifier = Modifier.width(20.dp))
        RadioButtonIndicator(
            checked = checked,
            modifier = Modifier
                .size(22.dp)
                .align(Alignment.CenterVertically)
        )
    }
}

@Composable
private fun RadioButtonIndicator(
    checked: Boolean,
    modifier: Modifier
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            checked,
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
                    color = LocalColors.current.text,
                    shape = CircleShape
                )
        ) {}
    }
}
