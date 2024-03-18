package org.session.libsession.messaging.messages.signal;

import org.session.libsession.messaging.messages.visible.OpenGroupInvitation;
import org.session.libsession.messaging.messages.visible.VisibleMessage;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsession.messaging.utilities.UpdateMessageData;

public class OutgoingTextMessage {
  private final Recipient recipient;
  private final String    message;
  private final int       subscriptionId;
  private final long      expiresIn;
  private final long      expireStartedAt;
  private final long      sentTimestampMillis;
  private boolean         isOpenGroupInvitation = false;

  public OutgoingTextMessage(Recipient recipient, String message, long expiresIn, long expireStartedAt, int subscriptionId, long sentTimestampMillis) {
    this.recipient      = recipient;
    this.message        = message;
    this.expiresIn      = expiresIn;
    this.expireStartedAt= expireStartedAt;
    this.subscriptionId = subscriptionId;
    this.sentTimestampMillis = sentTimestampMillis;
  }

  public static OutgoingTextMessage from(VisibleMessage message, Recipient recipient, long expiresInMillis, long expireStartedAt) {
    return new OutgoingTextMessage(recipient, message.getText(), expiresInMillis, expireStartedAt, -1, message.getSentTimestamp());
  }

  public static OutgoingTextMessage fromOpenGroupInvitation(OpenGroupInvitation openGroupInvitation, Recipient recipient, Long sentTimestamp, long expiresInMillis, long expireStartedAt) {
    String url = openGroupInvitation.getUrl();
    String name = openGroupInvitation.getName();
    if (url == null || name == null) { return null; }
    // FIXME: Doing toJSON() to get the body here is weird
    String body = UpdateMessageData.Companion.buildOpenGroupInvitation(url, name).toJSON();
    OutgoingTextMessage outgoingTextMessage = new OutgoingTextMessage(recipient, body, expiresInMillis, expireStartedAt, -1, sentTimestamp);
    outgoingTextMessage.isOpenGroupInvitation = true;
    return outgoingTextMessage;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public long getExpireStartedAt() {
    return expireStartedAt;
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public String getMessageBody() {
    return message;
  }

  public Recipient getRecipient() {
    return recipient;
  }

  public long getSentTimestampMillis() {
    return sentTimestampMillis;
  }

  public boolean isSecureMessage() {
    return true;
  }

  public boolean isOpenGroupInvitation() { return isOpenGroupInvitation; }
}
