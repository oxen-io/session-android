package org.thoughtcrime.securesms.groups.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.PreviewTheme

@Serializable
data class RouteEditGroupName(val name: String?, val description: String?)

@Composable
fun EditGroupNameScreen(
    name: String?,
    description: String?,
    navigateBackWithName: (name: String) -> Unit) {

    var newName by remember {
        mutableStateOf(name.orEmpty())
    }

    var newDescription by remember {
        mutableStateOf(description.orEmpty())
    }

    Box(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp)
            .background(LocalColors.current.background)
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
//                style = MaterialTheme.typography.subtitle1,
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
                            navigateBackWithName(newName)
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
                            navigateBackWithName("")
                        },
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalColors.current.danger
                )
            }
        }
    }
}



@Preview
@Composable
fun PreviewDialogChange() {
    PreviewTheme() {
        EditGroupNameScreen(name = null, description = null) {

        }
    }
}