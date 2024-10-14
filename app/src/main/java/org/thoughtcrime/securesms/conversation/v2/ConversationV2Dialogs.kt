package org.thoughtcrime.securesms.conversation.v2

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.EMOJI_KEY
import org.thoughtcrime.securesms.conversation.v2.ConversationViewModel.Commands.*
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.OpenURLAlertDialog
import org.thoughtcrime.securesms.ui.RadioOption
import org.thoughtcrime.securesms.ui.components.TitledRadioButton
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionMaterialTheme

@Composable
fun ConversationV2Dialogs(
    dialogsState: ConversationViewModel.DialogsState,
    sendCommand: (ConversationViewModel.Commands) -> Unit
){
    SessionMaterialTheme {
        // open link confirmation
        if(!dialogsState.openLinkDialogUrl.isNullOrEmpty()){
            OpenURLAlertDialog(
                url = dialogsState.openLinkDialogUrl,
                onDismissRequest = {
                    // hide dialog
                    sendCommand(ShowOpenUrlDialog(null))
                }
            )
        }

        // delete message(s) for everyone
        if(dialogsState.deleteEveryone != null){
            var deleteForEveryone by remember { mutableStateOf(dialogsState.deleteEveryone.defaultToEveryone)}

            AlertDialog(
                onDismissRequest = {
                    // hide dialog
                    sendCommand(HideDeleteEveryoneDialog)
                },
                title = pluralStringResource(
                    R.plurals.deleteMessage,
                    dialogsState.deleteEveryone.messages.size,
                    dialogsState.deleteEveryone.messages.size
                ),
                text = pluralStringResource(
                    R.plurals.deleteMessageConfirm,
                    dialogsState.deleteEveryone.messages.size,
                    dialogsState.deleteEveryone.messages.size
                ),
                content = {
                    // add warning text, if any
                    dialogsState.deleteEveryone.warning?.let {
                        Text(
                            text = it,
                            textAlign = TextAlign.Center,
                            style = LocalType.current.small,
                            color = LocalColors.current.warning,
                            modifier = Modifier.padding(
                                top = LocalDimensions.current.xxxsSpacing,
                                bottom = LocalDimensions.current.xxsSpacing
                            )
                        )
                    }

                    TitledRadioButton(
                        contentPadding = PaddingValues(
                            horizontal = LocalDimensions.current.xxsSpacing,
                            vertical = 0.dp
                        ),
                        option = RadioOption(
                            value = Unit,
                            title = GetString(stringResource(R.string.deleteMessageDeviceOnly)),
                            selected = !deleteForEveryone
                        )
                    ) {
                        deleteForEveryone = false
                    }

                    TitledRadioButton(
                        contentPadding = PaddingValues(
                            horizontal = LocalDimensions.current.xxsSpacing,
                            vertical = 0.dp
                        ),
                        option = RadioOption(
                            value = Unit,
                            title = GetString(stringResource(R.string.deleteMessageEveryone)),
                            selected = deleteForEveryone,
                            enabled = dialogsState.deleteEveryone.everyoneEnabled
                        )
                    ) {
                        deleteForEveryone = true
                    }
                },
                buttons = listOf(
                    DialogButtonModel(
                        text = GetString(stringResource(id = R.string.delete)),
                        color = LocalColors.current.danger,
                        onClick = {
                            // delete messages based on chosen option
                            sendCommand(
                                if(deleteForEveryone) MarkAsDeletedForEveryone(
                                    dialogsState.deleteEveryone.copy(defaultToEveryone = deleteForEveryone)
                                )
                                else MarkAsDeletedLocally(dialogsState.deleteEveryone.messages)
                            )
                        }
                    ),
                    DialogButtonModel(
                        GetString(stringResource(R.string.cancel))
                    )
                )
            )
        }

        // delete message(s) for all my devices
        if(dialogsState.deleteAllDevices != null){
            var deleteAllDevices by remember { mutableStateOf(dialogsState.deleteAllDevices.defaultToEveryone) }

            AlertDialog(
                onDismissRequest = {
                    // hide dialog
                    sendCommand(HideDeleteAllDevicesDialog)
                },
                title = pluralStringResource(
                    R.plurals.deleteMessage,
                    dialogsState.deleteAllDevices.messages.size,
                    dialogsState.deleteAllDevices.messages.size
                ),
                text = pluralStringResource(
                    R.plurals.deleteMessageConfirm,
                    dialogsState.deleteAllDevices.messages.size,
                    dialogsState.deleteAllDevices.messages.size
                ),
                content = {
                    TitledRadioButton(
                        contentPadding = PaddingValues(
                            horizontal = LocalDimensions.current.xxsSpacing,
                            vertical = 0.dp
                        ),
                        option = RadioOption(
                            value = Unit,
                            title = GetString(stringResource(R.string.deleteMessageDeviceOnly)),
                            selected = !deleteAllDevices
                        )
                    ) {
                        deleteAllDevices = false
                    }

                    TitledRadioButton(
                        contentPadding = PaddingValues(
                            horizontal = LocalDimensions.current.xxsSpacing,
                            vertical = 0.dp
                        ),
                        option = RadioOption(
                            value = Unit,
                            title = GetString(stringResource(R.string.deleteMessageDevicesAll)),
                            selected = deleteAllDevices
                        )
                    ) {
                        deleteAllDevices = true
                    }
                },
                buttons = listOf(
                    DialogButtonModel(
                        text = GetString(stringResource(id = R.string.delete)),
                        color = LocalColors.current.danger,
                        onClick = {
                            // delete messages based on chosen option
                            sendCommand(
                                if(deleteAllDevices) MarkAsDeletedForEveryone(
                                    dialogsState.deleteAllDevices.copy(defaultToEveryone = deleteAllDevices)
                                )
                                else MarkAsDeletedLocally(dialogsState.deleteAllDevices.messages)
                            )
                        }
                    ),
                    DialogButtonModel(
                        GetString(stringResource(R.string.cancel))
                    )
                )
            )
        }


        // Clear emoji
        if(dialogsState.clearAllEmoji != null){
            AlertDialog(
                onDismissRequest = {
                    // hide dialog
                    sendCommand(HideClearEmoji)
                },
                text = stringResource(R.string.emojiReactsClearAll).let { txt ->
                    Phrase.from(txt).put(EMOJI_KEY, dialogsState.clearAllEmoji.emoji).format().toString()
                },
                buttons = listOf(
                    DialogButtonModel(
                        text = GetString(stringResource(id = R.string.clear)),
                        color = LocalColors.current.danger,
                        onClick = {
                            // delete emoji
                            sendCommand(
                                ClearEmoji(dialogsState.clearAllEmoji.emoji, dialogsState.clearAllEmoji.messageId)
                            )
                        }
                    ),
                    DialogButtonModel(
                        GetString(stringResource(R.string.cancel))
                    )
                )
            )
        }
    }
}

@Preview
@Composable
fun PreviewURLDialog(){
    PreviewTheme {
        ConversationV2Dialogs(
            dialogsState = ConversationViewModel.DialogsState(
                openLinkDialogUrl = "https://google.com"
            ),
            sendCommand = {}
        )
    }
}
