package org.thoughtcrime.securesms.conversation.disappearingmessages.ui

import androidx.annotation.StringRes
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.RadioOption

typealias ExpiryOptionsCardData = OptionsCardData<ExpiryMode>

data class UiState(
    val cards: List<ExpiryOptionsCardData> = emptyList(),
    val showGroupFooter: Boolean = false,
    val showSetButton: Boolean = true
) {
    constructor(
        vararg cards: ExpiryOptionsCardData,
        showGroupFooter: Boolean = false,
        showSetButton: Boolean = true,
    ): this(
        cards.asList(),
        showGroupFooter,
        showSetButton
    )
}

data class OptionsCardData<T>(
    val title: GetString,
    val options: List<RadioOption<T>>
) {
    constructor(title: GetString, vararg options: RadioOption<T>): this(title, options.asList())
    constructor(@StringRes title: Int, vararg options: RadioOption<T>): this(GetString(title), options.asList())
}
