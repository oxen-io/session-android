package org.thoughtcrime.securesms.conversation.disappearingmessages

import androidx.annotation.StringRes
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.utilities.Address
import org.thoughtcrime.securesms.ui.GetString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

enum class Event {
    SUCCESS, FAIL
}

data class State(
    val isGroup: Boolean = false,
    val isSelfAdmin: Boolean = true,
    val address: Address? = null,
    val isNoteToSelf: Boolean = false,
    val expiryMode: ExpiryMode? = null,
    val isNewConfigEnabled: Boolean = true,
    val persistedMode: ExpiryMode? = null,
    val showDebugOptions: Boolean = false
) {
    val subtitle get() = when {
        isGroup || isNoteToSelf -> GetString(R.string.activity_disappearing_messages_subtitle_sent)
        else -> GetString(R.string.activity_disappearing_messages_subtitle)
    }

    val typeOptionsHidden get() = isNoteToSelf || (isGroup && isNewConfigEnabled)

    val nextType get() = when {
        expiryType == ExpiryType.AFTER_READ -> ExpiryType.AFTER_READ
        isNewConfigEnabled -> ExpiryType.AFTER_SEND
        else -> ExpiryType.LEGACY
    }

    val duration get() = expiryMode?.duration
    val expiryType get() = expiryMode?.type

    val isTimeOptionsEnabled = isNoteToSelf || isSelfAdmin && (isNewConfigEnabled || expiryType == ExpiryType.LEGACY)
}


enum class ExpiryType(
    private val createMode: (Long) -> ExpiryMode,
    @StringRes val title: Int,
    @StringRes val subtitle: Int? = null,
    @StringRes val contentDescription: Int = title,
) {
    NONE(
        { ExpiryMode.NONE },
        R.string.expiration_off,
        contentDescription = R.string.AccessibilityId_disable_disappearing_messages,
    ),
    LEGACY(
        ExpiryMode::Legacy,
        R.string.expiration_type_disappear_legacy,
        contentDescription = R.string.expiration_type_disappear_legacy_description
    ),
    AFTER_READ(
        ExpiryMode::AfterRead,
        R.string.expiration_type_disappear_after_read,
        R.string.expiration_type_disappear_after_read_description,
        R.string.AccessibilityId_disappear_after_read_option
    ),
    AFTER_SEND(
        ExpiryMode::AfterSend,
        R.string.expiration_type_disappear_after_send,
        R.string.expiration_type_disappear_after_read_description,
        R.string.AccessibilityId_disappear_after_send_option
    );

    fun mode(seconds: Long) = if (seconds != 0L) createMode(seconds) else ExpiryMode.NONE
    fun mode(duration: Duration) = mode(duration.inWholeSeconds)

    fun defaultMode(persistedMode: ExpiryMode?) = when(this) {
        persistedMode?.type -> persistedMode
        AFTER_READ -> mode(12.hours)
        else -> mode(1.days)
    }
}

val ExpiryMode.type: ExpiryType get() = when(this) {
    is ExpiryMode.Legacy -> ExpiryType.LEGACY
    is ExpiryMode.AfterSend -> ExpiryType.AFTER_SEND
    is ExpiryMode.AfterRead -> ExpiryType.AFTER_READ
    else -> ExpiryType.NONE
}
