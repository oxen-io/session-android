package org.thoughtcrime.securesms.notifications;

import static org.session.libsession.utilities.StringSubstitutionConstants.CONVERSATION_COUNT_KEY;
import static org.session.libsession.utilities.StringSubstitutionConstants.MESSAGE_COUNT_KEY;
import static org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.SpannableStringBuilder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.squareup.phrase.Phrase;
import java.util.LinkedList;
import java.util.List;
import network.loki.messenger.R;
import org.session.libsession.messaging.contacts.Contact;
import org.session.libsession.utilities.NotificationPrivacyPreference;
import org.session.libsession.utilities.Util;
import org.session.libsession.utilities.recipients.Recipient;
import org.thoughtcrime.securesms.database.SessionContactDatabase;
import org.thoughtcrime.securesms.dependencies.DatabaseComponent;
import org.thoughtcrime.securesms.home.HomeActivity;

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
    String txt = Phrase.from(context, R.string.notificationsSystem)
            .put(MESSAGE_COUNT_KEY, messageCount)
            .put(CONVERSATION_COUNT_KEY, threadCount)
            .format().toString();
    setSubText(txt);

    // Note: `setContentInfo` details are only visible in Android API 24 and below - as our minimum is now API 26 this can be skipped.
    //setContentInfo(String.valueOf(messageCount));

    setNumber(messageCount);
  }

  public void setMostRecentSender(Recipient recipient, Recipient threadRecipient) {
    String displayName = recipient.toShortString();
    if (threadRecipient.isGroupRecipient()) {
      displayName = getGroupDisplayName(recipient, threadRecipient.isCommunityRecipient());
    }
    if (privacy.isDisplayContact()) {
        String txt = Phrase.from(context, R.string.notificationsMostRecent)
            .put(NAME_KEY, displayName)
            .format().toString();
        setContentText(txt);
    }

    if (recipient.getNotificationChannel() != null) {
      setChannelId(recipient.getNotificationChannel());
    }
  }

  public void addActions(PendingIntent markAsReadIntent) {
    NotificationCompat.Action markAllAsReadAction = new NotificationCompat.Action(R.drawable.check,
                                            context.getString(R.string.messageMarkRead),
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
