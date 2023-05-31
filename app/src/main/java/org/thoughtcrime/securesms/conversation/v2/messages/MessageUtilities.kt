package org.thoughtcrime.securesms.conversation.v2.messages

import android.widget.TextView
import androidx.core.view.isVisible
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.util.DateUtil

private const val maxTimeBetweenBreaks = 5 * 60 * 1000L // 5 minutes

fun TextView.showDateBreak(dateUtil: DateUtil, message: MessageRecord, previous: MessageRecord?) {
    val showDateBreak = (previous == null || message.timestamp - previous.timestamp > maxTimeBetweenBreaks)
    isVisible = showDateBreak
    text = if (showDateBreak) dateUtil.format(message.timestamp) else ""
}
