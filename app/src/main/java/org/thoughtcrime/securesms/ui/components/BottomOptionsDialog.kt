package org.thoughtcrime.securesms.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType

/**
 * A bottom sheet dialog that displays a list of options.
 *
 * @param options The list of options to display.
 * @param onDismissRequest Callback to be invoked when the dialog is to be dismissed.
 * @param onOptionClick Callback to be invoked when an option is clicked.
 * @param optionTitle A function that returns the title of an option.
 * @param optionIconRes A function that returns the icon resource of an option.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> BottomOptionsDialog(
    options: Collection<T>,
    onDismissRequest: () -> Unit,
    onOptionClick: (T) -> Unit,
    optionTitle: (T) -> String,
    optionIconRes: (T) -> Int,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(
            topStart = LocalDimensions.current.xsSpacing,
            topEnd = LocalDimensions.current.xsSpacing
        ),
        dragHandle = {},
        containerColor = LocalColors.current.backgroundSecondary,
    ) {
        for (option in options) {
            BottomOptionItem(
                text = optionTitle(option),
                leadingIcon = optionIconRes(option),
                onClick = {
                    onOptionClick(option)
                    onDismissRequest()
                }
            )
        }
    }
}

@Composable
private fun BottomOptionItem(
    leadingIcon: Int,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(LocalDimensions.current.smallSpacing)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.spacing),
        verticalAlignment = CenterVertically,
    ) {
        Icon(
            painter = painterResource(leadingIcon),
            modifier = Modifier.size(LocalDimensions.current.iconMedium),
            tint = LocalColors.current.text,
            contentDescription = null
        )

        Text(
            modifier = Modifier.weight(1f),
            style = LocalType.current.large,
            text = text,
            textAlign = TextAlign.Start,
            color = LocalColors.current.text,
        )
    }
}


data class BottomOptionsDialogItem(
    val title: String,
    val iconRes: Int,
    val onClick: () -> Unit,
)

/**
 * A convenience function to display a [BottomOptionsDialog] with a collection of [BottomOptionsDialogItem].
 */
@Composable
fun BottomOptionsDialog(
    items: Collection<BottomOptionsDialogItem>,
    onDismissRequest: () -> Unit
) {
    BottomOptionsDialog(
        options = items,
        onDismissRequest = onDismissRequest,
        onOptionClick = { it.onClick() },
        optionTitle = { it.title },
        optionIconRes = { it.iconRes }
    )
}