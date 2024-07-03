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
package org.thoughtcrime.securesms.notifications;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.goterl.lazysodium.utils.KeyPair;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import me.leolin.shortcutbadger.ShortcutBadger;
import network.loki.messenger.R;
import org.session.libsession.messaging.open_groups.OpenGroup;
import org.session.libsession.messaging.sending_receiving.notifications.MessageNotifier;
import org.session.libsession.messaging.utilities.SessionId;
import org.session.libsession.messaging.utilities.SodiumUtilities;
import org.session.libsession.snode.SnodeAPI;
import org.session.libsession.utilities.Address;
import org.session.libsession.utilities.Contact;
import org.session.libsession.utilities.ServiceUtil;
import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsignal.utilities.IdPrefix;
import org.session.libsignal.utilities.Log;
import org.session.libsignal.utilities.Util;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.contacts.ContactUtil;
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2;
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionUtilities;
import org.thoughtcrime.securesms.crypto.KeyPairUtilities;
import org.thoughtcrime.securesms.database.LokiThreadDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.Quote;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.dependencies.DatabaseComponent;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.SessionMetaProtocol;
import org.thoughtcrime.securesms.util.SpanUtil;

/**
 * Handles posting system notifications for new messages.
 * @author Moxie Marlinspike
 */
public class DefaultMessageNotifier implements MessageNotifier {

    private static final String TAG = DefaultMessageNotifier.class.getSimpleName();

    public static final  String EXTRA_REMOTE_REPLY        = "extra_remote_reply";
    public static final  String LATEST_MESSAGE_ID_TAG     = "extra_latest_message_id";

    // Arbitrary IDs for various types of notifications
    private static final int    FOREGROUND_ID             = 313399;
    private static final int    SUMMARY_NOTIFICATION_ID   = 1338;
    private static final int    PENDING_MESSAGES_ID       = 1111;

    private static final String NOTIFICATION_GROUP        = "messages";

    // Don't make a notification sound more often than once every five seconds
    private static final long   MIN_AUDIBLE_PERIOD_MILLIS = TimeUnit.SECONDS.toMillis(5);

    // Don't ping the user about notifications more than once per minute
    private static final long   DESKTOP_ACTIVITY_PERIOD   = TimeUnit.MINUTES.toMillis(1);

    private volatile static       long               visibleThread                = -1;
    private volatile static       boolean            homeScreenVisible            = false;
    private volatile static       long               lastNotificationTimestamp    = -1;
    private volatile static       long               lastAudibleNotification      = -1;
    private          static final CancelableExecutor executor                     = new CancelableExecutor();

    @Override
    public void setVisibleThread(long threadId) {
    visibleThread = threadId;
    }

    @Override
    public void setHomeScreenVisible(boolean isVisible) {
    homeScreenVisible = isVisible;
    }

    @Override
    public void setLastNotificationTimestamp(long timestamp) {
        lastNotificationTimestamp = timestamp;
    }

    @Override
    public void notifyMessageDeliveryFailed(Context context, Recipient recipient, long threadId) {
        if (visibleThread != threadId) {
            Intent intent = new Intent(context, ConversationActivityV2.class);
            intent.putExtra(ConversationActivityV2.ADDRESS, recipient.getAddress());
            intent.putExtra(ConversationActivityV2.THREAD_ID, threadId);
            intent.setData((Uri.parse("custom://" + SnodeAPI.getNowWithOffset())));

            FailedNotificationBuilder builder = new FailedNotificationBuilder(context, TextSecurePreferences.getNotificationPrivacy(context), intent);
            ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).notify((int)threadId, builder.build());
        }
    }

    public void notifyMessagesPending(Context context) {
        if (!TextSecurePreferences.areNotificationsEnabled(context)) {
            return;
        }

        PendingMessageNotificationBuilder builder = new PendingMessageNotificationBuilder(context, TextSecurePreferences.getNotificationPrivacy(context));
        ServiceUtil.getNotificationManager(context).notify(PENDING_MESSAGES_ID, builder.build());
    }

    @Override
    public void cancelDelayedNotifications() {
    executor.cancel();
    }

    private void cancelActiveNotifications(@NonNull Context context) {
        NotificationManager notifications = ServiceUtil.getNotificationManager(context);

        // Cancel the summary notification..
        notifications.cancel(SUMMARY_NOTIFICATION_ID);

        // ..then should we have any other notifications cancel each one individually.
        // Note:
        StatusBarNotification[] activeNotifications = notifications.getActiveNotifications();
        boolean haveActiveNotifications = activeNotifications.length > 0;
        if (haveActiveNotifications) {
            try {
                for (StatusBarNotification activeNotification : activeNotifications) {
                    notifications.cancel(activeNotification.getId());
                }
            } catch (Throwable e) {
                Log.w(TAG, e);
                notifications.cancelAll();
            }
        }
    }

    private void cancelOrphanedNotifications(@NonNull Context context, NotificationState notificationState) {
        try {
            NotificationManager     notifications       = ServiceUtil.getNotificationManager(context);
            StatusBarNotification[] activeNotifications = notifications.getActiveNotifications();

            for (StatusBarNotification notification : activeNotifications) {
                boolean validNotification = false;

                if (notification.getId() != SUMMARY_NOTIFICATION_ID &&
                    notification.getId() != KeyCachingService.SERVICE_RUNNING_ID          &&
                    notification.getId() != FOREGROUND_ID         &&
                    notification.getId() != PENDING_MESSAGES_ID)
                {
                    for (NotificationItem item : notificationState.getNotifications()) {
                        if (notification.getId() == (SUMMARY_NOTIFICATION_ID + item.getThreadId())) {
                            validNotification = true;
                            break;
                        }
                    }

                    if (!validNotification) { notifications.cancel(notification.getId()); }
                }
            }
        } catch (Throwable e) {
            // XXX Android ROM Bug, see #6043
            Log.w(TAG, e);
        }
    }

    @Override
    public void updateNotificationWithoutSignalingAndResetReminderCount(@NonNull Context context) {
        if (TextSecurePreferences.areNotificationsDisabled(context)) { return; }
        updateNotification(context, false, 0);
    }

    @Override
    public void scheduleDelayedNotificationOrPassThroughToSpecificThreadAndSignal(@NonNull Context context, long threadId)
    {
        // If we've notified within the last minute then schedule a DELAYED notification to prevent
        // spamming the user with notifications..
        long millisSinceLastNotification = System.currentTimeMillis() - lastNotificationTimestamp;
        if (millisSinceLastNotification < DESKTOP_ACTIVITY_PERIOD) {
            Log.i(TAG, "Scheduling delayed notification...");
            executor.execute(new DelayedNotification(context, threadId));
        } else {
            // ..otherwise we can create a notification right now.
            // Note: We hit this `updateNotificationForSpecificThread` and then `BatchMessageReceiveJob`
            // hits it AGAIN - so only update the notification if we haven't already been notified.
            MmsSmsDatabase mmsSmsDatabase = DatabaseComponent.get(context).mmsSmsDatabase();
            int dbNotifiedCount = mmsSmsDatabase.getNotifiedCount(threadId);
            if (dbNotifiedCount == 0) {
                updateNotificationForSpecificThread(context, threadId, true);
            } else {
                Log.d(TAG, "Have already notified about thread " + threadId + " - not re-notifying.");
            }
        }
    }

    @Override
    public void updateNotificationForSpecificThread(@NonNull Context context, long threadId, boolean signalTheUser)
    {
        boolean        thisThreadIsVisible = visibleThread == threadId;
        ThreadDatabase threads             = DatabaseComponent.get(context).threadDatabase();
        Recipient      recipient           = threads.getRecipientForThreadId(threadId);

        // Don't show notifications if they're disabled or there's a recipient that is muted
        if (!TextSecurePreferences.areNotificationsEnabled(context) ||
                (recipient != null && recipient.isMuted()))
        {
            return;
        }

        // If the user is looking directly at the conversation or the home screen (where they'll see
        // the snippet update) then we don't need to notify them - they can literally see the msg.
        boolean canAlreadySeeUpdate = thisThreadIsVisible || homeScreenVisible;
        if (!canAlreadySeeUpdate) { // ACL TRY THIS WITHOUT --> || hasExistingNotifications(context)) {
            //Log.w("[ACL]", "We can't see this thread or home, or we already have a notification for this thread so opting to update notification - should signal?: " + signalTheUser);
            updateNotification(context, signalTheUser, 0);
        }
  }

  private boolean hasExistingNotifications(Context context) {
    NotificationManager notifications = ServiceUtil.getNotificationManager(context);
    try {
      StatusBarNotification[] activeNotifications = notifications.getActiveNotifications();
      return activeNotifications.length > 0;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public void updateNotification(@NonNull Context context, boolean shouldSignal, int reminderCount)
  {
      try (Cursor telcoCursor = DatabaseComponent.get(context).mmsSmsDatabase().getUnread()) {

          // Don't show notifications if the user hasn't seen the welcome screen yet
          if ((telcoCursor == null || telcoCursor.isAfterLast()) || !TextSecurePreferences.hasSeenWelcomeScreen(context)) {
              updateBadge(context, 0);
              cancelActiveNotifications(context);
              clearReminder(context);
              return;
          }

          NotificationState notificationState = constructNotificationState(context, telcoCursor);

          // If we were asked to audibly signal but the minimum audible period hasn't elapsed then flip
          // our flag so that we don't make a sound.
          long millisSinceLastNotification = System.currentTimeMillis() - lastNotificationTimestamp;
          boolean haveAlreadyNotifiedRecently = millisSinceLastNotification < MIN_AUDIBLE_PERIOD_MILLIS;
          if (shouldSignal && haveAlreadyNotifiedRecently) { shouldSignal = false; }

          // If we're going to signal the user then update the last audible notification time to now
          if (shouldSignal) { lastAudibleNotification = System.currentTimeMillis(); }

          try {
              if (notificationState.hasMultipleThreads()) {
                  for (long threadId : notificationState.getThreads()) {
                      sendSingleThreadNotification(context, new NotificationState(notificationState.getNotificationsForThread(threadId)), false, true);
                  }
                  sendMultipleThreadNotification(context, notificationState, shouldSignal);
              } else if (notificationState.getMessageCount() > 0) {
                  // If we do NOT have multiple threads and have at least one message then notify
                  sendSingleThreadNotification(context, notificationState, shouldSignal, false);
              } else {
                  // If we do NOT have multiple threads and do NOT have at least one message
                  cancelActiveNotifications(context);
              }
          } catch (Exception e) {
              Log.e(TAG, "Error creating notification", e);
          }
          cancelOrphanedNotifications(context, notificationState);
          updateBadge(context, notificationState.getMessageCount());

          if (shouldSignal) { scheduleReminder(context, reminderCount); }
      }
  }

  private void sendSingleThreadNotification(@NonNull  Context context,
                                            @NonNull  NotificationState notificationState,
                                            boolean signal, boolean bundled)
  {
    Log.i(TAG, "sendSingleThreadNotification()  signal: " + signal + "  bundled: " + bundled);

    if (notificationState.getNotifications().isEmpty()) {
      if (!bundled) cancelActiveNotifications(context);
      Log.i(TAG, "Empty notification state. Skipping.");
      return;
    }

    SingleRecipientNotificationBuilder builder        = new SingleRecipientNotificationBuilder(context, TextSecurePreferences.getNotificationPrivacy(context));
    List<NotificationItem>             notifications  = notificationState.getNotifications();
    Recipient                          recipient      = notifications.get(0).getRecipient();
    int                                notificationId = (int) (SUMMARY_NOTIFICATION_ID + (bundled ? notifications.get(0).getThreadId() : 0));
    String                             messageIdTag   = String.valueOf(notifications.get(0).getTimestamp());

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);

    // Notifications can be bundled together in Android R (API 30) and above - so if that's the case
    // and we already have
    for (StatusBarNotification notification: notificationManager.getActiveNotifications()) {
      if ( (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && notification.isAppGroup() == bundled)
              && messageIdTag.equals(notification.getNotification().extras.getString(LATEST_MESSAGE_ID_TAG))) {
        return;
      }
    }

    // Set when this notification occurred so that notifications can be sorted by date
    long timestamp = notifications.get(0).getTimestamp();
    if (timestamp != 0) builder.setWhen(timestamp);

    builder.putStringExtra(LATEST_MESSAGE_ID_TAG, messageIdTag);

    CharSequence text = notifications.get(0).getText();
    CharSequence body = MentionUtilities.highlightMentions(
              text != null ? text : "",
              false,
              false,
              true, // no styling here, only text formatting
              notifications.get(0).getThreadId(),
              context
    );

    builder.setThread(notifications.get(0).getRecipient());
    builder.setMessageCount(notificationState.getMessageCount());



    // TODO: Removing highlighting mentions in the notification because this context is the libsession one which
    // TODO: doesn't have access to the `R.attr.message_sent_text_color` and `R.attr.message_received_text_color`
    // TODO: attributes to perform the colour lookup. Also, it makes little sense to highlight the mentions using
    // TODO: the app theme as it may result in insufficient contrast with the notification background which will
    // TODO: be using the SYSTEM theme.
    builder.setPrimaryMessageBody(recipient,
                                  notifications.get(0).getIndividualRecipient(),
                                  text != null ? body : "",
                                  notifications.get(0).getSlideDeck());

    builder.setContentIntent(notifications.get(0).getPendingIntent(context));
    builder.setDeleteIntent(notificationState.getDeleteIntent(context));
    builder.setOnlyAlertOnce(!signal);
    builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
    builder.setAutoCancel(true);

    ReplyMethod replyMethod = ReplyMethod.forRecipient(context, recipient);

    boolean canReply = SessionMetaProtocol.canUserReplyToNotification(recipient);

    PendingIntent quickReplyIntent = canReply ? notificationState.getQuickReplyIntent(context, recipient) :  null;
    PendingIntent remoteReplyIntent = canReply ? notificationState.getRemoteReplyIntent(context, recipient, replyMethod) : null;

    builder.addActions(notificationState.getMarkAsReadIntent(context, notificationId),
            quickReplyIntent,
            remoteReplyIntent,
            replyMethod);

    if (canReply) {
      builder.addAndroidAutoAction(notificationState.getAndroidAutoReplyIntent(context, recipient),
              notificationState.getAndroidAutoHeardIntent(context, notificationId),
              notifications.get(0).getTimestamp());
    }

    ListIterator<NotificationItem> iterator = notifications.listIterator(notifications.size());

    while(iterator.hasPrevious()) {
      NotificationItem item = iterator.previous();
      builder.addMessageBody(item.getRecipient(), item.getIndividualRecipient(), item.getText());
    }

    if (signal) {
      builder.setAlarms(notificationState.getRingtone(context), notificationState.getVibrate());
      builder.setTicker(notifications.get(0).getIndividualRecipient(),
              notifications.get(0).getText());
    }

    if (bundled) {
      builder.setGroup(NOTIFICATION_GROUP);
      builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
    }

    Notification notification = builder.build();
    NotificationManagerCompat.from(context).notify(notificationId, notification);
    Log.i(TAG, "Posted notification. " + notification.toString());
  }

  private void sendMultipleThreadNotification(@NonNull  Context context,
                                              @NonNull  NotificationState notificationState,
                                              boolean signal)
  {
    Log.i(TAG, "sendMultiThreadNotification()  signal: " + signal);

    MultipleRecipientNotificationBuilder builder       = new MultipleRecipientNotificationBuilder(context, TextSecurePreferences.getNotificationPrivacy(context));
    List<NotificationItem>               notifications = notificationState.getNotifications();

    builder.setMessageCount(notificationState.getMessageCount(), notificationState.getThreadCount());
    builder.setMostRecentSender(notifications.get(0).getIndividualRecipient(), notifications.get(0).getRecipient());
    builder.setGroup(NOTIFICATION_GROUP);
    builder.setDeleteIntent(notificationState.getDeleteIntent(context));
    builder.setOnlyAlertOnce(!signal);
    builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
    builder.setAutoCancel(true);

    String messageIdTag = String.valueOf(notifications.get(0).getTimestamp());

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
    for (StatusBarNotification notification: notificationManager.getActiveNotifications()) {
      if (notification.getId() == SUMMARY_NOTIFICATION_ID
              && messageIdTag.equals(notification.getNotification().extras.getString(LATEST_MESSAGE_ID_TAG))) {
        return;
      }
    }

    long timestamp = notifications.get(0).getTimestamp();
    if (timestamp != 0) builder.setWhen(timestamp);

    builder.addActions(notificationState.getMarkAsReadIntent(context, SUMMARY_NOTIFICATION_ID));

    ListIterator<NotificationItem> iterator = notifications.listIterator(notifications.size());

    while(iterator.hasPrevious()) {
      NotificationItem item = iterator.previous();

      CharSequence body = MentionUtilities.highlightMentions(
              item.getText() != null ? item.getText() : "",
              false,
              false,
              true, // no styling here, only text formatting
              item.getThreadId(),
              context
      );

      builder.addMessageBody(item.getIndividualRecipient(), item.getRecipient(), body);
    }

    if (signal) {
      builder.setAlarms(notificationState.getRingtone(context), notificationState.getVibrate());
      CharSequence text = notifications.get(0).getText();

      CharSequence body = MentionUtilities.highlightMentions(
              text != null ? text : "",
              false,
              false,
              true, // no styling here, only text formatting
              notifications.get(0).getThreadId(),
              context
      );

      builder.setTicker(notifications.get(0).getIndividualRecipient(), body);
    }

    builder.putStringExtra(LATEST_MESSAGE_ID_TAG, messageIdTag);

    Notification notification = builder.build();
    NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, notification);
    Log.i(TAG, "Posted notification: " + notification);
  }

  private NotificationState constructNotificationState(@NonNull  Context context, @NonNull  Cursor cursor)
  {
      NotificationState notificationState = new NotificationState();
      MmsSmsDatabase mmsSmsDatabase = DatabaseComponent.get(context).mmsSmsDatabase();
      MmsSmsDatabase.Reader reader = mmsSmsDatabase.readerFor(cursor);
      ThreadDatabase threadDatabase = DatabaseComponent.get(context).threadDatabase();

      MessageRecord record;
      Map<Long, String> cache = new HashMap<Long, String>();

      while ((record = reader.getNext()) != null) {
        long         id                    = record.getId();
          boolean      mms                   = record.isMms() || record.isMmsNotification();
          Recipient    recipient             = record.getIndividualRecipient(); // ACL - WTF is the difference between individual and conversation recipient
          Recipient    conversationRecipient = record.getRecipient();
          Recipient    sender                = null; // The account sending the message to us
          long         threadId              = record.getThreadId();
          CharSequence body                  = record.getDisplayBody(context);
          SlideDeck    slideDeck             = null;
          long         timestamp             = record.getTimestamp();
          boolean      isMessageRequest      = false;

        if (threadId == -1) {
            Log.d(TAG, "Cannot construct a notification state for thread -1 - skipping.");
            continue;
        }

        // Attempt to obtain the sender of the message on the given thread
        sender = threadDatabase.getRecipientForThreadId(threadId);
        if (sender == null) {
            Log.d(TAG, "Could not identify sender of message - skipping this particular constructNotificationState pass.");
            continue;
        }

        if (sender.isMuted() || sender.isBlocked()) {
            Log.w("[ACL]", "Sender is muted or blocked - skipping notification from them.");
            continue;
        }

        Log.w("[ACL]", "In constructNotificationState: Sender name is: " + sender.getProfileName());

        boolean senderHasPreviouslySent = threadDatabase.getLastSeenAndHasSent(threadId).second();
        Log.w("[ACL]", "Sender has previously sent a msg?: " + senderHasPreviouslySent);

        // Determine if this message is a message request
        isMessageRequest = !sender.isGroupRecipient() && !sender.isApproved() && !senderHasPreviouslySent;

        Log.w("[ACL]", "Is this a msg req?: " + isMessageRequest + " - group recip? " + sender.isGroupRecipient() + ", approved?: " + sender.isApproved() + ", hasPrevSent?: " + senderHasPreviouslySent);

        // The following line is currently unused but keep around for potential future debugging
        //boolean threadDatabaseContainsMessageAboutThread = threadDatabase.getMessageCount(threadId) >= 1;

        // Only provide a single notification regarding any given thread
        boolean alreadyHaveActiveNotificationForThread = notificationState.getThreads().contains(threadId);
        boolean alreadyHaveAtLeastOneNotificationRegardingThreadInDb = mmsSmsDatabase.getNotifiedCount(threadId) >= 1;

        // ACL this is just whether the user has temporarily hidden the message requests block
        //boolean hasHiddenMessageRequests = TextSecurePreferences.hasHiddenMessageRequests(context);

        if (isMessageRequest && (alreadyHaveActiveNotificationForThread || alreadyHaveAtLeastOneNotificationRegardingThreadInDb)) {
            Log.w("[ACL]", "Got msg req but bailing from cns: alreadyHaveNotificationForThread: " + alreadyHaveActiveNotificationForThread);
            continue;
        }

      // ACL: This entire block of spaghetti is evil and needs to be cleaned up
      if (isMessageRequest) {
        body = SpanUtil.italic(context.getString(R.string.message_requests_notification));
      } else if (KeyCachingService.isLocked(context)) {
        body = SpanUtil.italic(context.getString(R.string.MessageNotifier_locked_message));
      } else if (record.isMms() && !((MmsMessageRecord) record).getSharedContacts().isEmpty()) {
        Contact contact = ((MmsMessageRecord) record).getSharedContacts().get(0);
        body = ContactUtil.getStringSummary(context, contact);
      } else if (record.isMms() && TextUtils.isEmpty(body) && !((MmsMessageRecord) record).getSlideDeck().getSlides().isEmpty()) {
        slideDeck = ((MediaMmsMessageRecord)record).getSlideDeck();
        body = SpanUtil.italic(slideDeck.getBody());
      } else if (record.isMms() && !record.isMmsNotification() && !((MmsMessageRecord) record).getSlideDeck().getSlides().isEmpty()) {
        slideDeck = ((MediaMmsMessageRecord)record).getSlideDeck();
        String message      = slideDeck.getBody() + ": " + record.getBody();
        int    italicLength = message.length() - body.length();
        body = SpanUtil.italic(message, italicLength);
      } else if (record.isOpenGroupInvitation()) {
        body = SpanUtil.italic(context.getString(R.string.ThreadRecord_open_group_invitation));
      }
      String userPublicKey = TextSecurePreferences.getLocalNumber(context);
      String blindedPublicKey = cache.get(threadId);
      if (blindedPublicKey == null) {
        blindedPublicKey = generateBlindedId(threadId, context);
        cache.put(threadId, blindedPublicKey);
      }



        if (sender.notifyType == RecipientDatabase.NOTIFY_TYPE_MENTIONS) {
          // check if mentioned here
          boolean isQuoteMentioned = false;
          if (record instanceof MmsMessageRecord) {
            Quote quote = ((MmsMessageRecord) record).getQuote();
            Address quoteAddress = quote != null ? quote.getAuthor() : null;
            String serializedAddress = quoteAddress != null ? quoteAddress.serialize() : null;
            isQuoteMentioned = (serializedAddress!= null && Objects.equals(userPublicKey, serializedAddress)) ||
                    (blindedPublicKey != null && Objects.equals(userPublicKey, blindedPublicKey));
          }
          if (body.toString().contains("@"+userPublicKey) || body.toString().contains("@"+blindedPublicKey) || isQuoteMentioned) {
            notificationState.addNotification(new NotificationItem(id, mms, recipient, conversationRecipient, sender, threadId, body, timestamp, slideDeck));
          }
        } else if (sender.notifyType == RecipientDatabase.NOTIFY_TYPE_NONE) {
          // do nothing, no notifications
        } else {
          notificationState.addNotification(new NotificationItem(id, mms, recipient, conversationRecipient, sender, threadId, body, timestamp, slideDeck));
        }

        String userBlindedPublicKey = blindedPublicKey;
        Optional<ReactionRecord> lastReact = Stream.of(record.getReactions())
                .filter(r -> !(r.getAuthor().equals(userPublicKey) || r.getAuthor().equals(userBlindedPublicKey)))
                .findLast();

        if (lastReact.isPresent()) {
          if (!sender.isGroupRecipient()) {
            ReactionRecord reaction = lastReact.get();
            Recipient reactor = Recipient.from(context, Address.fromSerialized(reaction.getAuthor()), false);
            String emoji = context.getString(R.string.reaction_notification, reactor.toShortString(), reaction.getEmoji());
            notificationState.addNotification(new NotificationItem(id, mms, reactor, reactor, sender, threadId, emoji, reaction.getDateSent(), slideDeck));
          }
        }

    }

    reader.close();
    return notificationState;
  }

  private @Nullable String generateBlindedId(long threadId, Context context) {
    LokiThreadDatabase lokiThreadDatabase   = DatabaseComponent.get(context).lokiThreadDatabase();
    OpenGroup openGroup = lokiThreadDatabase.getOpenGroupChat(threadId);
    KeyPair edKeyPair = KeyPairUtilities.INSTANCE.getUserED25519KeyPair(context);
    if (openGroup != null && edKeyPair != null) {
      KeyPair blindedKeyPair = SodiumUtilities.blindedKeyPair(openGroup.getPublicKey(), edKeyPair);
      if (blindedKeyPair != null) {
        return new SessionId(IdPrefix.BLINDED, blindedKeyPair.getPublicKey().getAsBytes()).getHexString();
      }
    }
    return null;
  }

  private void updateBadge(Context context, int count) {
    try {
      if (count == 0) ShortcutBadger.removeCount(context);
      else            ShortcutBadger.applyCount(context, count);
    } catch (Throwable t) {
      // NOTE :: I don't totally trust this thing, so I'm catching everything.
      Log.w("MessageNotifier", t);
    }
  }

  private void scheduleReminder(Context context, int count) {
    if (count >= TextSecurePreferences.getRepeatAlertsCount(context)) {
      return;
    }

    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    Intent       alarmIntent  = new Intent(ReminderReceiver.REMINDER_ACTION);
    alarmIntent.putExtra("reminder_count", count);

    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    long          timeout       = TimeUnit.MINUTES.toMillis(2);

    alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + timeout, pendingIntent);
  }

  @Override
  public void clearReminder(Context context) {
    Intent        alarmIntent   = new Intent(ReminderReceiver.REMINDER_ACTION);
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    AlarmManager  alarmManager  = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarmManager.cancel(pendingIntent);
  }

  public static class ReminderReceiver extends BroadcastReceiver {

    public static final String REMINDER_ACTION = "network.loki.securesms.MessageNotifier.REMINDER_ACTION";

    @SuppressLint("StaticFieldLeak")
    @Override
    public void onReceive(final Context context, final Intent intent) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          int reminderCount = intent.getIntExtra("reminder_count", 0);
          ApplicationContext.getInstance(context).messageNotifier.updateNotification(context, true, reminderCount + 1);

          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  private static class DelayedNotification implements Runnable {

    private static final long DELAY = TimeUnit.SECONDS.toMillis(5);

    private final AtomicBoolean canceled = new AtomicBoolean(false);

    private final Context context;
    private final long    threadId;
    private final long    delayUntil;

    private DelayedNotification(Context context, long threadId) {
      this.context    = context;
      this.threadId   = threadId;
      this.delayUntil = System.currentTimeMillis() + DELAY;
    }

    @Override
    public void run() {
      long delayMillis = delayUntil - System.currentTimeMillis();
      Log.i(TAG, "Waiting to notify: " + delayMillis);

      if (delayMillis > 0) {
        Util.sleep(delayMillis);
      }

      if (!canceled.get()) {
        Log.i(TAG, "Not canceled, notifying...");
        ApplicationContext.getInstance(context).messageNotifier.updateNotificationForSpecificThread(context, threadId, true);
        ApplicationContext.getInstance(context).messageNotifier.cancelDelayedNotifications();
      } else {
        Log.w(TAG, "Canceled, not notifying...");
      }
    }

    public void cancel() {
      canceled.set(true);
    }
  }

  private static class CancelableExecutor {

    private final Executor                 executor = Executors.newSingleThreadExecutor();
    private final Set<DelayedNotification> tasks    = new HashSet<>();

    public void execute(final DelayedNotification runnable) {
      synchronized (tasks) {
        tasks.add(runnable);
      }

      Runnable wrapper = new Runnable() {
        @Override
        public void run() {
          runnable.run();

          synchronized (tasks) {
            tasks.remove(runnable);
          }
        }
      };

      executor.execute(wrapper);
    }

    public void cancel() {
      synchronized (tasks) {
        for (DelayedNotification task : tasks) {
          task.cancel();
        }
      }
    }
  }
}
