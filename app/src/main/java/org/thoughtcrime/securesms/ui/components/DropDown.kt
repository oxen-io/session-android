package org.thoughtcrime.securesms.ui.components

import androidx.compose.foundation.border
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.bold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropDown(
    modifier: Modifier = Modifier,
    selectedText: String,
    values: List<String>,
    onValueSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = {
            expanded = !expanded
        }
    ) {
        TextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .border(
                    1.dp,
                    color = LocalColors.current.borders,
                    shape = MaterialTheme.shapes.medium
                ),
            shape = MaterialTheme.shapes.medium,
            colors = ExposedDropdownMenuDefaults.textFieldColors(
                focusedContainerColor = LocalColors.current.backgroundSecondary,
                unfocusedContainerColor = LocalColors.current.backgroundSecondary,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledTrailingIconColor = LocalColors.current.primary,
                errorTrailingIconColor = LocalColors.current.primary,
                focusedTrailingIconColor = LocalColors.current.primary,
                unfocusedTrailingIconColor = LocalColors.current.primary,
                disabledTextColor = LocalColors.current.text,
                errorTextColor = LocalColors.current.text,
                focusedTextColor = LocalColors.current.text,
                unfocusedTextColor = LocalColors.current.text
            ),
            textStyle = LocalType.current.base.bold()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            values.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item,
                            style = LocalType.current.base
                        )
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = LocalColors.current.text
                    ),
                    onClick = {
                        expanded = false
                        onValueSelected(item)
                    }
                )
            }
        }
    }
}

@Preview
@Composable
fun PreviewDropDown() {
    PreviewTheme {
        DropDown(
            selectedText = "Hello",
            values = listOf("First Item", "Second Item", "Third Item"),
            onValueSelected = {})
    }
}