package org.thoughtcrime.securesms.conversation.v2

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.thoughtcrime.securesms.conversation.disappearingmessages.ExpiryType
import org.thoughtcrime.securesms.ui.OpenURLAlertDialog
import org.thoughtcrime.securesms.ui.theme.SessionMaterialTheme
import org.thoughtcrime.securesms.conversation.v2.ConversationViewModel.Commands.*
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.RadioOption
import org.thoughtcrime.securesms.ui.components.TitledRadioButton
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import kotlin.time.Duration.Companion.days

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

        // delete message(s) on device only
        if(dialogsState.deleteDeviceOnly != null){
            AlertDialog(
                onDismissRequest = {
                    // hide dialog
                    sendCommand(HideDeleteDeviceOnlyDialog)
                },
                title = pluralStringResource(
                    R.plurals.deleteMessage,
                    dialogsState.deleteDeviceOnly.size,
                    dialogsState.deleteDeviceOnly.size
                ),
                text = stringResource(R.string.deleteMessageDescriptionDevice), //todo DELETION we need the plural version of this here, which currently is not set up in strings
                buttons = listOf(
                    DialogButtonModel(
                        text = GetString(stringResource(id = R.string.delete)),
                        color = LocalColors.current.danger,
                        onClick = {
                            sendCommand(MarkAsDeletedLocally(dialogsState.deleteDeviceOnly))
                        }
                    ),
                    DialogButtonModel(
                        GetString(stringResource(R.string.cancel))
                    )
                )
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
                text = stringResource(R.string.deleteMessageConfirm), //todo DELETION we need the plural version of this here, which currently is not set up in strings
                content = {
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
                            selected = deleteForEveryone
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
                                if(deleteForEveryone) MarkAsDeletedForEveryone(dialogsState.deleteEveryone.messages)
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
            var deleteAllDevices by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = {
                    // hide dialog
                    sendCommand(HideDeleteAllDevicesDialog)
                },
                title = pluralStringResource(
                    R.plurals.deleteMessage,
                    dialogsState.deleteAllDevices.size,
                    dialogsState.deleteAllDevices.size
                ),
                text = stringResource(R.string.deleteMessageConfirm), //todo DELETION we need the plural version of this here, which currently is not set up in strings
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
                                if(deleteAllDevices) MarkAsDeletedForEveryone(dialogsState.deleteAllDevices)
                                else MarkAsDeletedLocally(dialogsState.deleteAllDevices)
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
