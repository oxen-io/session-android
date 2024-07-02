package org.thoughtcrime.securesms.groups.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.result.ResultBackNavigator
import com.ramcosta.composedestinations.spec.DestinationStyle
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.LocalExtraColors
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.ThemeResPreviewParameterProvider

@EditGroupNavGraph
@Composable
@Destination(style = DestinationStyle.Dialog::class)
fun EditClosedGroupNameScreen(
    resultNavigator: ResultBackNavigator<String>
) {
    EditClosedGroupNameView { name ->
        if (name.isEmpty()) {
            resultNavigator.navigateBack()
        } else {
            resultNavigator.navigateBack(name)
        }
    }
}

@Composable
fun EditClosedGroupNameView(navigateBack: (String) -> Unit) {

    var newName by remember {
        mutableStateOf("")
    }

    var newDescription by remember {
        mutableStateOf("")
    }

    Box(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp)
            .background(MaterialTheme.colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(id = R.string.dialog_edit_group_information_title),
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(id = R.string.dialog_edit_group_information_message),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                maxLines = 1,
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.dialog_edit_group_information_enter_group_name)
                    )
                }
            )
            OutlinedTextField(
                value = newDescription,
                onValueChange = { newDescription = it },
                modifier = Modifier
                    .fillMaxWidth(),
                minLines = 2,
                maxLines = 2,
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.dialog_edit_group_information_enter_group_description)
                    )
                }
            )
            Row(modifier = Modifier
                .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text(
                    text = stringResource(R.string.save),
                    modifier = Modifier
                        .padding(16.dp)
                        .weight(1f)
                        .clickable {
                            navigateBack(newName)
                        },
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.cancel),
                    modifier = Modifier
                        .padding(16.dp)
                        .weight(1f)
                        .clickable {
                            navigateBack("")
                        },
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalExtraColors.current.destructive
                )
            }
        }
    }
}



@Preview
@Composable
fun PreviewDialogChange(@PreviewParameter(ThemeResPreviewParameterProvider::class) styleRes: Int) {

    PreviewTheme(themeResId = styleRes) {
        EditClosedGroupNameView {

        }
    }

}