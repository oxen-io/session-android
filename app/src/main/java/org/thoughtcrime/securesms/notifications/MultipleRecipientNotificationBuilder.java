package org.thoughtcrime.securesms.notifications;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.session.libsession.messaging.contacts.Contact;
import org.session.libsession.utilities.NotificationPrivacyPreference;
import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.Util;
import org.session.libsession.utilities.recipients.Recipient;
import org.thoughtcrime.securesms.database.SessionContactDatabase;
import org.thoughtcrime.securesms.dependencies.DatabaseComponent;
import org.thoughtcrime.securesms.home.HomeActivity;

import java.util.LinkedList;
import java.util.List;

import network.loki.messenger.R;

public class MultipleRecipientNotificationBuilder extends AbstractNotificationBuilder {

  private final List<CharSequence> messageBodies = new LinkedList<>();

  public MultipleRecipientNotificationBuilder(Context context, NotificationPrivacyPreference privacy) {
    super(context, privacy);

    setColor(context.getResources().getColor(R.color.textsecure_primary));
    setSmallIcon(R.drawable.ic_notification);
    setContentTitle(context.getString(R.string.app_name));
    setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, HomeActivity.class), PendingIntent.FLAG_IMMUTABLE));
    setCategory(NotificationCompat.CATEGORY_MESSAGE);
    setGroupSummary(true);
  }

  public void setMessageCount(int messageCount, int threadCount) {
    setSubText(context.getString(R.string.MessageNotifier_d_new_messages_in_d_conversations,
                                 messageCount, threadCount));
    setContentInfo(String.valueOf(messageCount));
    setNumber(messageCount);
  }

  public void setMostRecentSender(Recipient recipient, Recipient threadRecipient) {
    String displayName = recipient.toShortString();
    if (threadRecipient.isGroupRecipient()) {
      displayName = getGroupDisplayName(recipient, threadRecipient.isCommunityRecipient());
    }
    if (privacy.isDisplayContact()) {
      setContentText(context.getString(R.string.MessageNotifier_most_recent_from_s, displayName));
    }

    if (recipient.getNotificationChannel() != null) {
      setChannelId(recipient.getNotificationChannel());
    }
  }

  public void addActions(PendingIntent markAsReadIntent) {
    NotificationCompat.Action markAllAsReadAction = new NotificationCompat.Action(R.drawable.check,
                                            context.getString(R.string.MessageNotifier_mark_all_as_read),
                                            markAsReadIntent);
    addAction(markAllAsReadAction);
    extend(new NotificationCompat.WearableExtender().addAction(markAllAsReadAction));
  }

  public void putStringExtra(String key, String value) {
    extras.putString(key,value);
  }

  public void addMessageBody(@NonNull Recipient sender, Recipient threadRecipient, @Nullable CharSequence body) {
    String displayName = sender.toShortString();
    if (threadRecipient.isGroupRecipient()) {
      displayName = getGroupDisplayName(sender, threadRecipient.isCommunityRecipient());
    }
    if (privacy.isDisplayMessage()) {
      SpannableStringBuilder builder = new SpannableStringBuilder();
      builder.append(Util.getBoldedString(displayName));
      builder.append(": ");
      builder.append(body == null ? "" : body);

      messageBodies.add(builder);
    } else if (privacy.isDisplayContact()) {
      messageBodies.add(Util.getBoldedString(displayName));
    }

    if (privacy.isDisplayContact() && sender.getContactUri() != null) {
//      addPerson(sender.getContactUri().toString());
    }
  }

  @Override
  public Notification build() {
    if (privacy.isDisplayMessage() || privacy.isDisplayContact()) {
      NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();

      for (CharSequence body : messageBodies) {
        style.addLine(trimToDisplayLength(body));
      }

      setStyle(style);
    }

    return super.build();
  }

  /**
   * @param recipient          the * individual * recipient for which to get the display name.
   * @param openGroupRecipient whether in an open group context
   */
  private String getGroupDisplayName(Recipient recipient, boolean openGroupRecipient) {
    SessionContactDatabase contactDB = DatabaseComponent.get(context).sessionContactDatabase();
    String accountID = recipient.getAddress().serialize();
    Contact contact = contactDB.getContactWithAccountID(accountID);
    if (contact == null) { return accountID; }
    String displayName = contact.displayName(openGroupRecipient ? Contact.ContactContext.OPEN_GROUP : Contact.ContactContext.REGULAR);
    if (displayName == null) { return accountID; }
    return displayName;
  }
}
