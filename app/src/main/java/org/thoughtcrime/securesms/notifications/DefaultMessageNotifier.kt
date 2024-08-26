/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.AsyncTask
import android.os.Build
import android.text.TextUtils
import android.widget.Toast
import androidx.camera.core.impl.utils.ContextUtil.getApplicationContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.annimon.stream.Stream
import com.squareup.phrase.Phrase
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.Volatile
import me.leolin.shortcutbadger.ShortcutBadger
import network.loki.messenger.R
import org.session.libsession.messaging.sending_receiving.notifications.MessageNotifier
import org.session.libsession.messaging.utilities.AccountId
import org.session.libsession.messaging.utilities.SodiumUtilities.blindedKeyPair
import org.session.libsession.utilities.Address.Companion.fromSerialized
import org.session.libsession.utilities.ServiceUtil
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.EMOJI_KEY
import org.session.libsession.utilities.TextSecurePreferences.Companion.getLocalNumber
import org.session.libsession.utilities.TextSecurePreferences.Companion.getNotificationPrivacy
import org.session.libsession.utilities.TextSecurePreferences.Companion.getRepeatAlertsCount
import org.session.libsession.utilities.TextSecurePreferences.Companion.hasHiddenMessageRequests
import org.session.libsession.utilities.TextSecurePreferences.Companion.isNotificationsEnabled
import org.session.libsession.utilities.TextSecurePreferences.Companion.removeHasHiddenMessageRequests
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Util
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.contacts.ContactUtil
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionUtilities.highlightMentions
import org.thoughtcrime.securesms.crypto.KeyPairUtilities.getUserED25519KeyPair
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent.Companion.get
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.preferences.ShareLogsDialog
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.SessionMetaProtocol.canUserReplyToNotification
import org.thoughtcrime.securesms.util.SpanUtil

/**
 * Handles posting system notifications for new messages.
 *
 *
 * @author Moxie Marlinspike
 */
class DefaultMessageNotifier : MessageNotifier {
    override fun setVisibleThread(threadId: Long) {
        visibleThread = threadId
    }

    override fun setHomeScreenVisible(isVisible: Boolean) {
        homeScreenVisible = isVisible
    }

    override fun setLastDesktopActivityTimestamp(timestamp: Long) {
        lastDesktopActivityTimestamp = timestamp
    }

    override fun notifyMessageDeliveryFailed(context: Context?, recipient: Recipient?, threadId: Long) {
        // We do not provide notifications for message delivery failure.
    }

    override fun cancelDelayedNotifications() {
        executor.cancel()
    }

    private fun cancelActiveNotifications(context: Context): Boolean {
        val notifications = ServiceUtil.getNotificationManager(context)
        val hasNotifications = notifications.activeNotifications.size > 0
        notifications.cancel(SUMMARY_NOTIFICATION_ID)

        try {
            val activeNotifications = notifications.activeNotifications

            for (activeNotification in activeNotifications) {
                notifications.cancel(activeNotification.id)
            }
        } catch (e: Throwable) {
            // XXX Appears to be a ROM bug, see #6043
            Log.w(TAG, e)
            notifications.cancelAll()
        }
        return hasNotifications
    }

    private fun cancelOrphanedNotifications(context: Context, notificationState: NotificationState) {
        try {
            val notifications = ServiceUtil.getNotificationManager(context)
            val activeNotifications = notifications.activeNotifications

            for (notification in activeNotifications) {
                var validNotification = false

                if (notification.id != SUMMARY_NOTIFICATION_ID && notification.id != KeyCachingService.SERVICE_RUNNING_ID && notification.id != FOREGROUND_ID && notification.id != PENDING_MESSAGES_ID) {
                    for (item in notificationState.notifications) {
                        if (notification.id.toLong() == (SUMMARY_NOTIFICATION_ID + item.threadId)) {
                            validNotification = true
                            break
                        }
                    }

                    if (!validNotification) {
                        notifications.cancel(notification.id)
                    }
                }
            }
        } catch (e: Throwable) {
            // XXX Android ROM Bug, see #6043
            Log.w(TAG, e)
        }
    }

    override fun updateNotification(context: Context) {
        if (!isNotificationsEnabled(context)) {
            return
        }

        updateNotification(context, false, 0)
    }

    override fun updateNotification(context: Context, threadId: Long) {
        if (System.currentTimeMillis() - lastDesktopActivityTimestamp < DESKTOP_ACTIVITY_PERIOD) {
            Log.i(TAG, "Scheduling delayed notification...")
            executor.execute(DelayedNotification(context, threadId))
        } else {
            updateNotification(context, threadId, true)
        }
    }

    override fun updateNotification(context: Context, threadId: Long, signal: Boolean) {
        val isVisible = visibleThread == threadId

        val threads = get(context).threadDatabase()
        val recipient = threads.getRecipientForThreadId(threadId)

        if (recipient != null && !recipient.isGroupRecipient && threads.getMessageCount(threadId) == 1 &&
            !(recipient.isApproved || threads.getLastSeenAndHasSent(threadId).second())
        ) {
            removeHasHiddenMessageRequests(context)
        }

        if (!isNotificationsEnabled(context) ||
            (recipient != null && recipient.isMuted)
        ) {
            return
        }

        if ((!isVisible && !homeScreenVisible) || hasExistingNotifications(context)) {
            updateNotification(context, signal, 0)
        }
    }

    private fun hasExistingNotifications(context: Context): Boolean {
        val notifications = ServiceUtil.getNotificationManager(context)
        try {
            val activeNotifications = notifications.activeNotifications
            return activeNotifications.isNotEmpty()
        } catch (e: Exception) {
            return false
        }
    }

    override fun updateNotification(context: Context, signal: Boolean, reminderCount: Int) {
        var playNotificationAudio = signal // Local copy of the argument so we can modify it
        var telcoCursor: Cursor? = null
        val pushCursor: Cursor? = null

        try {
            telcoCursor = get(context).mmsSmsDatabase().unread // TODO: add a notification specific lighter query here

            if ((telcoCursor == null || telcoCursor.isAfterLast) || getLocalNumber(context) == null) {
                updateBadge(context, 0)
                cancelActiveNotifications(context)
                clearReminder(context)
                return
            }

            try {
                val notificationState = constructNotificationState(context, telcoCursor)

                if (playNotificationAudio && (System.currentTimeMillis() - lastAudibleNotification) < MIN_AUDIBLE_PERIOD_MILLIS) {
                    playNotificationAudio = false
                } else if (playNotificationAudio) {
                    lastAudibleNotification = System.currentTimeMillis()
                }

                if (notificationState.hasMultipleThreads()) {
                    for (threadId in notificationState.threads) {
                        sendSingleThreadNotification(context, NotificationState(notificationState.getNotificationsForThread(threadId)), false, true)
                    }
                    sendMultipleThreadNotification(context, notificationState, playNotificationAudio)
                } else if (notificationState.messageCount > 0) {
                    sendSingleThreadNotification(context, notificationState, playNotificationAudio, false)
                } else {
                    cancelActiveNotifications(context)
                }

                cancelOrphanedNotifications(context, notificationState)
                updateBadge(context, notificationState.messageCount)

                if (playNotificationAudio) {
                    scheduleReminder(context, reminderCount)
                }
            }
            catch (e: Exception) {
                Log.e(TAG, "Error creating notification", e)
            }

        } finally {
            telcoCursor?.close()
        }
    }

    // Note: The `signal` parameter means "play an audio signal for the notification".
    private fun sendSingleThreadNotification(
        context: Context,
        notificationState: NotificationState,
        signal: Boolean,
        bundled: Boolean
    ) {
        Log.i(TAG, "sendSingleThreadNotification()  signal: $signal  bundled: $bundled")

        if (notificationState.notifications.isEmpty()) {
            if (!bundled) cancelActiveNotifications(context)
            Log.i(TAG, "Empty notification state. Skipping.")
            return
        }

        val builder = SingleRecipientNotificationBuilder(context, getNotificationPrivacy(context))
        val notifications = notificationState.notifications
        val recipient = notifications[0].recipient
        val notificationId = (SUMMARY_NOTIFICATION_ID + (if (bundled) notifications[0].threadId else 0)).toInt()
        val messageIdTag = notifications[0].timestamp.toString()

        val notificationManager = ServiceUtil.getNotificationManager(context)
        for (notification in notificationManager.activeNotifications) {
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && notification.isAppGroup == bundled)
                && (messageIdTag == notification.notification.extras.getString(LATEST_MESSAGE_ID_TAG))
            ) {
                return
            }
        }

        val timestamp = notifications[0].timestamp
        if (timestamp != 0L) builder.setWhen(timestamp)

        builder.putStringExtra(LATEST_MESSAGE_ID_TAG, messageIdTag)

        val text = notifications[0].text

        builder.setThread(notifications[0].recipient)
        builder.setMessageCount(notificationState.messageCount)

        val builderCS = text ?: ""
        val ss = highlightMentions(
            builderCS,
            false,
            false,
            true,
            if (bundled) notifications[0].threadId else 0,
            context
        )

        builder.setPrimaryMessageBody(
            recipient,
            notifications[0].individualRecipient,
            ss,
            notifications[0].slideDeck
        )

        builder.setContentIntent(notifications[0].getPendingIntent(context))
        builder.setDeleteIntent(notificationState.getDeleteIntent(context))
        builder.setOnlyAlertOnce(!signal)
        builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        builder.setAutoCancel(true)

        val replyMethod = ReplyMethod.forRecipient(context, recipient)

        val canReply = canUserReplyToNotification(recipient)

        val quickReplyIntent = if (canReply) notificationState.getQuickReplyIntent(context, recipient) else null
        val remoteReplyIntent = if (canReply) notificationState.getRemoteReplyIntent(context, recipient, replyMethod) else null

        builder.addActions(
            notificationState.getMarkAsReadIntent(context, notificationId),
            quickReplyIntent,
            remoteReplyIntent,
            replyMethod
        )

        if (canReply) {
            builder.addAndroidAutoAction(
                notificationState.getAndroidAutoReplyIntent(context, recipient),
                notificationState.getAndroidAutoHeardIntent(context, notificationId),
                notifications[0].timestamp
            )
        }

        val iterator: ListIterator<NotificationItem> = notifications.listIterator(notifications.size)

        while (iterator.hasPrevious()) {
            val item = iterator.previous()
            builder.addMessageBody(item.recipient, item.individualRecipient, item.text)
        }

        if (signal) {
            builder.setAlarms(notificationState.getRingtone(context), notificationState.vibrate)
            builder.setTicker(
                notifications[0].individualRecipient,
                notifications[0].text
            )
        }

        if (bundled) {
            builder.setGroup(NOTIFICATION_GROUP)
            builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        }

        val notification = builder.build()

        // TODO - ACL to fix this properly & will do on 2024-08-26, but just skipping for now so review can start
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        Log.i(TAG, "Posted notification. $notification")
    }

    // Note: The `signal` parameter means "play an audio signal for the notification".
    private fun sendMultipleThreadNotification(
        context: Context,
        notificationState: NotificationState,
        signal: Boolean
    ) {
        Log.i(TAG, "sendMultiThreadNotification()  signal: $signal")

        val builder = MultipleRecipientNotificationBuilder(context, getNotificationPrivacy(context))
        val notifications = notificationState.notifications

        builder.setMessageCount(notificationState.messageCount, notificationState.threadCount)
        builder.setMostRecentSender(notifications[0].individualRecipient, notifications[0].recipient)
        builder.setGroup(NOTIFICATION_GROUP)
        builder.setDeleteIntent(notificationState.getDeleteIntent(context))
        builder.setOnlyAlertOnce(!signal)
        builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        builder.setAutoCancel(true)

        val messageIdTag = notifications[0].timestamp.toString()

        val notificationManager = ServiceUtil.getNotificationManager(context)
        for (notification in notificationManager.activeNotifications) {
            if (notification.id == SUMMARY_NOTIFICATION_ID && messageIdTag == notification.notification.extras.getString(LATEST_MESSAGE_ID_TAG)) {
                return
            }
        }

        val timestamp = notifications[0].timestamp
        if (timestamp != 0L) builder.setWhen(timestamp)

        builder.addActions(notificationState.getMarkAsReadIntent(context, SUMMARY_NOTIFICATION_ID))

        val iterator: ListIterator<NotificationItem> = notifications.listIterator(notifications.size)
        while (iterator.hasPrevious()) {
            val item = iterator.previous()
            builder.addMessageBody(
                item.individualRecipient, item.recipient,
                highlightMentions(
                    (if (item.text != null) item.text else "")!!,
                    false,
                    false,
                    true,  // no styling here, only text formatting
                    item.threadId,
                    context
                )
            )
        }

        if (signal) {
            builder.setAlarms(notificationState.getRingtone(context), notificationState.vibrate)
            val text = notifications[0].text
            builder.setTicker(
                notifications[0].individualRecipient,
                highlightMentions(
                    text ?: "",
                    false,
                    false,
                    true,  // no styling here, only text formatting
                    notifications[0].threadId,
                    context
                )
            )
        }

        builder.putStringExtra(LATEST_MESSAGE_ID_TAG, messageIdTag)

        // TODO - ACL to fix this properly & will do on 2024-08-26, but just skipping for now so review can start
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }

        val notification = builder.build()
        NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, notification)
        Log.i(TAG, "Posted notification. $notification")
    }

    private fun constructNotificationState(context: Context, cursor: Cursor): NotificationState {
        val notificationState = NotificationState()
        val reader = get(context).mmsSmsDatabase().readerFor(cursor)
        if (reader == null) {
            Log.e(TAG, "No reader for cursor - aborting constructNotificationState")
            return NotificationState()
        }

        val threadDatabase = get(context).threadDatabase()
        val cache: MutableMap<Long, String?> = HashMap()

        // CAREFUL: Do not put this loop back as `while ((reader.next.also { record = it }) != null) {` because it breaks with a Null Pointer Exception!
        var record: MessageRecord? = null
        do {
            record = reader.next
            if (record == null) break // Bail if there are no more MessageRecords

            val id = record.getId()
            val mms = record.isMms || record.isMmsNotification
            val recipient = record.individualRecipient
            val conversationRecipient = record.recipient
            val threadId = record.threadId
            var body: CharSequence = record.getDisplayBody(context)
            var threadRecipients: Recipient? = null
            var slideDeck: SlideDeck? = null
            val timestamp = record.timestamp
            var messageRequest = false

            if (threadId != -1L) {
                threadRecipients = threadDatabase.getRecipientForThreadId(threadId)
                messageRequest = threadRecipients != null && !threadRecipients.isGroupRecipient &&
                        !threadRecipients.isApproved && !threadDatabase.getLastSeenAndHasSent(threadId).second()
                if (messageRequest && (threadDatabase.getMessageCount(threadId) > 1 || !hasHiddenMessageRequests(context))) {
                    continue
                }
            }

            // If this is a message request from an unknown user..
            if (messageRequest) {
                body = SpanUtil.italic(context.getString(R.string.messageRequestsNew))

            // If we received some manner of notification but Session is locked..
            } else if (KeyCachingService.isLocked(context)) {
                // Note: We provide 1 because `messageNewYouveGot` is now a plurals string and we don't have a count yet, so just
                // giving it 1 will result in "You got a new message".
                body = SpanUtil.italic(context.resources.getQuantityString(R.plurals.messageNewYouveGot, 1, 1))

            // ----- Note: All further cases assume we know the contact and that Session isn't locked -----

            // If this is a notification about a multimedia message from a contact we know about..
            } else if (record.isMms && !(record as MmsMessageRecord).sharedContacts.isEmpty()) {
                val contact = (record as MmsMessageRecord).sharedContacts[0]
                body = ContactUtil.getStringSummary(context, contact)

                // If this is a notification about a multimedia message which contains no text but DOES contain a slide deck with at least one slide..
            } else if (record.isMms && TextUtils.isEmpty(body) && !(record as MmsMessageRecord).slideDeck.slides.isEmpty()) {
                slideDeck = (record as MediaMmsMessageRecord).slideDeck
                body = SpanUtil.italic(slideDeck.body)

                // If this is a notification about a multimedia message, but it's not ITSELF a multimedia notification AND it contains a slide deck with at least one slide..
            } else if (record.isMms && !record.isMmsNotification && !(record as MmsMessageRecord).slideDeck.slides.isEmpty()) {
                slideDeck = (record as MediaMmsMessageRecord).slideDeck
                val message = slideDeck.body + ": " + record.body
                val italicLength = message.length - body.length
                body = SpanUtil.italic(message, italicLength)

                // If this is a notification about an invitation to a community..
            } else if (record.isOpenGroupInvitation) {
                body = SpanUtil.italic(context.getString(R.string.communityInvitation))
            }

            val userPublicKey = getLocalNumber(context)
            var blindedPublicKey = cache[threadId]
            if (blindedPublicKey == null) {
                blindedPublicKey = generateBlindedId(threadId, context)
                cache[threadId] = blindedPublicKey
            }
            if (threadRecipients == null || !threadRecipients.isMuted) {
                if (threadRecipients != null && threadRecipients.notifyType == RecipientDatabase.NOTIFY_TYPE_MENTIONS) {
                    // check if mentioned here
                    var isQuoteMentioned = false
                    if (record is MmsMessageRecord) {
                        val quote = (record as MmsMessageRecord).quote
                        val quoteAddress = quote?.author
                        val serializedAddress = quoteAddress?.serialize()
                        isQuoteMentioned = (serializedAddress != null && userPublicKey == serializedAddress) ||
                                (blindedPublicKey != null && userPublicKey == blindedPublicKey)
                    }
                    if (body.toString().contains("@$userPublicKey") || body.toString().contains("@$blindedPublicKey") || isQuoteMentioned) {
                        notificationState.addNotification(NotificationItem(id, mms, recipient, conversationRecipient, threadRecipients, threadId, body, timestamp, slideDeck))
                    }
                } else if (threadRecipients != null && threadRecipients.notifyType == RecipientDatabase.NOTIFY_TYPE_NONE) {
                    // do nothing, no notifications
                } else {
                    notificationState.addNotification(NotificationItem(id, mms, recipient, conversationRecipient, threadRecipients, threadId, body, timestamp, slideDeck))
                }

                val userBlindedPublicKey = blindedPublicKey
                val lastReact = Stream.of(record.reactions)
                    .filter { r: ReactionRecord -> !(r.author == userPublicKey || r.author == userBlindedPublicKey) }
                    .findLast()

                if (lastReact.isPresent) {
                    if (threadRecipients != null && !threadRecipients.isGroupRecipient) {
                        val reaction = lastReact.get()
                        val reactor = Recipient.from(context, fromSerialized(reaction.author), false)
                        val emoji = Phrase.from(context, R.string.emojiReactsNotification).put(EMOJI_KEY, reaction.emoji).format().toString()
                        notificationState.addNotification(NotificationItem(id, mms, reactor, reactor, threadRecipients, threadId, emoji, reaction.dateSent, slideDeck))
                    }
                }
            }
        } while (record != null) // This will never hit because we break early if we get a null record at the start of the do..while loop

        reader.close()
        return notificationState
    }

    private fun generateBlindedId(threadId: Long, context: Context): String? {
        val lokiThreadDatabase = get(context).lokiThreadDatabase()
        val openGroup = lokiThreadDatabase.getOpenGroupChat(threadId)
        val edKeyPair = getUserED25519KeyPair(context)
        if (openGroup != null && edKeyPair != null) {
            val blindedKeyPair = blindedKeyPair(openGroup.publicKey, edKeyPair)
            if (blindedKeyPair != null) {
                return AccountId(IdPrefix.BLINDED, blindedKeyPair.publicKey.asBytes).hexString
            }
        }
        return null
    }

    private fun updateBadge(context: Context, count: Int) {
        try {
            if (count == 0) ShortcutBadger.removeCount(context)
            else ShortcutBadger.applyCount(context, count)
        } catch (t: Throwable) {
            Log.w("MessageNotifier", t)
        }
    }

    private fun scheduleReminder(context: Context, count: Int) {
        if (count >= getRepeatAlertsCount(context)) {
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(ReminderReceiver.REMINDER_ACTION)
        alarmIntent.putExtra("reminder_count", count)

        val pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val timeout = TimeUnit.MINUTES.toMillis(2)

        alarmManager[AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + timeout] = pendingIntent
    }

    override fun clearReminder(context: Context) {
        val alarmIntent = Intent(ReminderReceiver.REMINDER_ACTION)
        val pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    class ReminderReceiver : BroadcastReceiver() {
        @SuppressLint("StaticFieldLeak")
        override fun onReceive(context: Context, intent: Intent) {
            object : AsyncTask<Void?, Void?, Void?>() {

                override fun doInBackground(vararg params: Void?): Void? {
                    val reminderCount = intent.getIntExtra("reminder_count", 0)
                    ApplicationContext.getInstance(context).messageNotifier.updateNotification(context, true, reminderCount + 1)
                    return null
                }

            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }

        companion object {
            const val REMINDER_ACTION: String = "network.loki.securesms.MessageNotifier.REMINDER_ACTION"
        }
    }

    // ACL: What is the concept behind delayed notifications? Why would we ever want this? To batch them up so
    // that we get a bunch of notifications once per minute or something rather than a constant stream of them
    // if that's what was incoming?!?
    private class DelayedNotification(private val context: Context, private val threadId: Long) : Runnable {
        private val canceled = AtomicBoolean(false)

        private val delayUntil: Long

        init {
            this.delayUntil = System.currentTimeMillis() + DELAY
        }

        override fun run() {
            val delayMillis = delayUntil - System.currentTimeMillis()
            Log.i(TAG, "Waiting to notify: $delayMillis")

            if (delayMillis > 0) { Util.sleep(delayMillis) }

            if (!canceled.get()) {
                Log.i(TAG, "Not canceled, notifying...")
                ApplicationContext.getInstance(context).messageNotifier.updateNotification(context, threadId, true)
                ApplicationContext.getInstance(context).messageNotifier.cancelDelayedNotifications()
            } else {
                Log.w(TAG, "Canceled, not notifying...")
            }
        }

        fun cancel() {
            canceled.set(true)
        }

        companion object {
            private val DELAY = TimeUnit.SECONDS.toMillis(5)
        }
    }

    private class CancelableExecutor {
        private val executor: Executor = Executors.newSingleThreadExecutor()
        private val tasks: MutableSet<DelayedNotification> = HashSet()

        fun execute(runnable: DelayedNotification) {
            synchronized(tasks) { tasks.add(runnable) }

            val wrapper = Runnable {
                runnable.run()
                synchronized(tasks) {
                    tasks.remove(runnable)
                }
            }

            executor.execute(wrapper)
        }

        fun cancel() {
            synchronized(tasks) {
                for (task in tasks) { task.cancel() }
            }
        }
    }

    companion object {
        private val TAG: String = DefaultMessageNotifier::class.java.simpleName

        const val EXTRA_REMOTE_REPLY: String = "extra_remote_reply"
        const val LATEST_MESSAGE_ID_TAG: String = "extra_latest_message_id"

        private const val FOREGROUND_ID = 313399
        private const val SUMMARY_NOTIFICATION_ID = 1338
        private const val PENDING_MESSAGES_ID = 1111
        private const val NOTIFICATION_GROUP = "messages"
        private val MIN_AUDIBLE_PERIOD_MILLIS = TimeUnit.SECONDS.toMillis(5)
        private val DESKTOP_ACTIVITY_PERIOD = TimeUnit.MINUTES.toMillis(1)

        @Volatile
        private var visibleThread: Long = -1

        @Volatile
        private var homeScreenVisible = false

        @Volatile
        private var lastDesktopActivityTimestamp: Long = -1

        @Volatile
        private var lastAudibleNotification: Long = -1
        private val executor = CancelableExecutor()
    }
}
