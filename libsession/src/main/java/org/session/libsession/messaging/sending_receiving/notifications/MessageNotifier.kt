package org.session.libsession.messaging.sending_receiving.notifications

import android.content.Context
import org.session.libsession.utilities.recipients.Recipient

interface MessageNotifier {
    fun setHomeScreenVisible(isVisible: Boolean)
    fun setVisibleThread(threadId: Long)
    fun setLastNotificationTimestamp(timestamp: Long)
    fun notifyMessageDeliveryFailed(context: Context?, recipient: Recipient?, threadId: Long)
    fun cancelDelayedNotifications()
    fun updateNotificationWithoutSignalingAndResetReminderCount(context: Context)
    fun scheduleDelayedNotificationOrPassThroughToSpecificThreadAndSignal(context: Context, threadId: Long)
    fun updateNotificationForSpecificThread(context: Context, threadId: Long, signal: Boolean)
    fun updateNotification(context: Context, signal: Boolean, reminderCount: Int)
    fun clearReminder(context: Context)
}
